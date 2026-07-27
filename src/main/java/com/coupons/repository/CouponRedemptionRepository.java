package com.coupons.repository;

import com.coupons.domain.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    boolean existsByCouponIdAndUserId(Long couponId, String userId);
}
