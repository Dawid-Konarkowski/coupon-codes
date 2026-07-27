package com.empik.coupons.service.exception;

public class DuplicateCouponCodeException extends CouponException {

    public DuplicateCouponCodeException(String code) {
        super(CouponErrorCode.DUPLICATE_CODE, "A coupon with code '" + code + "' already exists.");
    }
}
