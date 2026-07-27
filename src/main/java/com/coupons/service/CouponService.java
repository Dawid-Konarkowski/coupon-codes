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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CouponService {

    private static final Logger log = LoggerFactory.getLogger(CouponService.class);

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    public CouponService(CouponRepository couponRepository,
                         CouponRedemptionRepository redemptionRepository) {
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
    }

    @Transactional
    public Coupon createCoupon(String code, int maxUses, String country) {
        String normalizedCode = Coupon.normalizeCode(code);
        if (couponRepository.existsByCode(normalizedCode)) {
            throw new DuplicateCouponCodeException(normalizedCode);
        }
        try {
            Coupon saved = couponRepository.saveAndFlush(new Coupon(code, maxUses, country));
            log.info("Created coupon '{}' (maxUses={}, country={})", saved.getCode(), maxUses, saved.getCountry());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request created the same code between the existence check and the insert.
            throw new DuplicateCouponCodeException(normalizedCode);
        }
    }

    /**
     * Registers a single use of a coupon.
     *
     * <p>Checks are ordered so the client gets the most meaningful error and no side effect happens
     * on a rejected request: existence, then country restriction, then per-user uniqueness, then the
     * atomic usage-limit consumption. All of it runs in one transaction, so if the coupon turns out
     * to be exhausted the per-user redemption row inserted earlier is rolled back.
     *
     * @param country the ISO country code the request originates from (already resolved from the IP)
     * @param userId  optional user identifier; when present, enforces one redemption per user
     * @return the coupon in its post-redemption state
     */
    @Transactional
    public Coupon redeem(String code, String country, String userId) {
        String normalizedCode = Coupon.normalizeCode(code);
        Coupon coupon = couponRepository.findByCode(normalizedCode)
                .orElseThrow(() -> new CouponNotFoundException(normalizedCode));

        if (!coupon.isForCountry(country)) {
            throw new CountryNotAllowedException(coupon.getCode(), country, coupon.getCountry());
        }

        if (StringUtils.hasText(userId)) {
            registerUserRedemption(coupon, userId);
        }

        int consumed = couponRepository.tryConsume(coupon.getId());
        if (consumed == 0) {
            // Rolls back the per-user redemption row inserted above, if any.
            throw new CouponExhaustedException(coupon.getCode());
        }

        Coupon updated = couponRepository.findById(coupon.getId()).orElseThrow();
        log.info("Redeemed coupon '{}' ({}/{}) for user '{}'",
                updated.getCode(), updated.getCurrentUses(), updated.getMaxUses(),
                StringUtils.hasText(userId) ? userId : "anonymous");
        return updated;
    }

    private void registerUserRedemption(Coupon coupon, String userId) {
        if (redemptionRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            throw new CouponAlreadyRedeemedException(coupon.getCode(), userId);
        }
        try {
            redemptionRepository.saveAndFlush(new CouponRedemption(coupon.getId(), userId));
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent redeems for the same (coupon, user) — the unique constraint decides the winner.
            throw new CouponAlreadyRedeemedException(coupon.getCode(), userId);
        }
    }
}
