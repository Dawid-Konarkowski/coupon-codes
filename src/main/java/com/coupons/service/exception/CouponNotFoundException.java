package com.coupons.service.exception;

public class CouponNotFoundException extends CouponException {

    public CouponNotFoundException(String code) {
        super(CouponErrorCode.COUPON_NOT_FOUND, "Coupon '" + code + "' does not exist.");
    }
}
