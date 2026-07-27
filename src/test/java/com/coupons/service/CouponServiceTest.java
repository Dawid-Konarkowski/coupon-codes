package com.coupons.service;

import com.coupons.domain.Coupon;
import com.coupons.domain.CouponRedemption;
import com.coupons.repository.CouponRedemptionRepository;
import com.coupons.repository.CouponRepository;
import com.coupons.service.exception.CouponAlreadyRedeemedException;
import com.coupons.service.exception.CouponExhaustedException;
import com.coupons.service.exception.CouponNotFoundException;
import com.coupons.service.exception.CountryNotAllowedException;
import com.coupons.service.exception.DuplicateCouponCodeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the service orchestration and error branches — no Spring context, no database.
 * The DB-level guarantees (atomic limit, unique per-user constraint) are covered by the integration
 * tests; here we verify the decision logic and that rejected requests trigger no side effects.
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    CouponRepository couponRepository;
    @Mock
    CouponRedemptionRepository redemptionRepository;
    @InjectMocks
    CouponService service;

    private Coupon coupon(String code, int maxUses, String country) {
        Coupon coupon = new Coupon(code, maxUses, country);
        ReflectionTestUtils.setField(coupon, "id", UUID.randomUUID());
        return coupon;
    }

    // --- createCoupon ---------------------------------------------------------------------------

    @Test
    void createCouponNormalizesAndSaves() {
        when(couponRepository.existsByCode("WIOSNA")).thenReturn(false);
        when(couponRepository.saveAndFlush(any(Coupon.class))).thenAnswer(inv -> inv.getArgument(0));

        Coupon created = service.createCoupon("wiosna", 5, "pl");

        assertThat(created.getCode()).isEqualTo("WIOSNA");
        assertThat(created.getCountry()).isEqualTo("PL");
        assertThat(created.getMaxUses()).isEqualTo(5);
        assertThat(created.getCurrentUses()).isZero();
    }

    @Test
    void createCouponRejectsDuplicateCode() {
        when(couponRepository.existsByCode("WIOSNA")).thenReturn(true);

        assertThatThrownBy(() -> service.createCoupon("WIOSNA", 5, "PL"))
                .isInstanceOf(DuplicateCouponCodeException.class);
        verify(couponRepository, never()).saveAndFlush(any());
    }

    @Test
    void createCouponMapsConstraintViolationToDuplicate() {
        when(couponRepository.existsByCode("WIOSNA")).thenReturn(false);
        when(couponRepository.saveAndFlush(any(Coupon.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.createCoupon("WIOSNA", 5, "PL"))
                .isInstanceOf(DuplicateCouponCodeException.class);
    }

    // --- redeem ---------------------------------------------------------------------------------

    @Test
    void redeemUnknownCouponThrowsNotFound() {
        when(couponRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem("nope", "PL", null))
                .isInstanceOf(CouponNotFoundException.class);
        verify(couponRepository, never()).tryConsume(any());
    }

    @Test
    void redeemFromWrongCountryIsRejectedWithoutConsuming() {
        Coupon coupon = coupon("WIOSNA", 5, "PL");
        when(couponRepository.findByCode("WIOSNA")).thenReturn(Optional.of(coupon));

        assertThatThrownBy(() -> service.redeem("WIOSNA", "DE", null))
                .isInstanceOf(CountryNotAllowedException.class);
        verify(couponRepository, never()).tryConsume(any());
        verify(redemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeemExhaustedCouponThrowsAndDoesNotReload() {
        Coupon coupon = coupon("WIOSNA", 1, "PL");
        when(couponRepository.findByCode("WIOSNA")).thenReturn(Optional.of(coupon));
        when(couponRepository.tryConsume(coupon.getId())).thenReturn(0);

        assertThatThrownBy(() -> service.redeem("WIOSNA", "PL", null))
                .isInstanceOf(CouponExhaustedException.class);
        verify(couponRepository, never()).findById(any());
    }

    @Test
    void redeemAnonymousSuccessConsumesAndReturnsFreshState() {
        Coupon coupon = coupon("WIOSNA", 2, "PL");
        when(couponRepository.findByCode("WIOSNA")).thenReturn(Optional.of(coupon));
        when(couponRepository.tryConsume(coupon.getId())).thenReturn(1);
        when(couponRepository.findById(coupon.getId())).thenReturn(Optional.of(coupon));

        Coupon result = service.redeem("wiosna", "pl", null);

        assertThat(result).isSameAs(coupon);
        verify(redemptionRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeemWithUserIdRecordsRedemption() {
        Coupon coupon = coupon("WIOSNA", 2, "PL");
        when(couponRepository.findByCode("WIOSNA")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), "user-1")).thenReturn(false);
        when(couponRepository.tryConsume(coupon.getId())).thenReturn(1);
        when(couponRepository.findById(coupon.getId())).thenReturn(Optional.of(coupon));

        service.redeem("WIOSNA", "PL", "user-1");

        verify(redemptionRepository).saveAndFlush(any(CouponRedemption.class));
    }

    @Test
    void redeemRejectsAlreadyRedeemedUserBeforeConsuming() {
        Coupon coupon = coupon("WIOSNA", 5, "PL");
        when(couponRepository.findByCode("WIOSNA")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), "user-1")).thenReturn(true);

        assertThatThrownBy(() -> service.redeem("WIOSNA", "PL", "user-1"))
                .isInstanceOf(CouponAlreadyRedeemedException.class);
        verify(redemptionRepository, never()).saveAndFlush(any());
        verify(couponRepository, never()).tryConsume(any());
    }

    @Test
    void redeemMapsRedemptionConstraintViolationToAlreadyRedeemed() {
        Coupon coupon = coupon("WIOSNA", 5, "PL");
        when(couponRepository.findByCode("WIOSNA")).thenReturn(Optional.of(coupon));
        when(redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), "user-1")).thenReturn(false);
        when(redemptionRepository.saveAndFlush(any(CouponRedemption.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate (coupon_id, user_id)"));

        assertThatThrownBy(() -> service.redeem("WIOSNA", "PL", "user-1"))
                .isInstanceOf(CouponAlreadyRedeemedException.class);
        verify(couponRepository, never()).tryConsume(any());
    }
}
