package com.coupons.service.exception;

public class CouponExhaustedException extends CouponException {

    public CouponExhaustedException(String code) {
        super(CouponErrorCode.COUPON_EXHAUSTED, "Coupon '" + code + "' has reached its maximum number of uses.");
    }
}
