package com.coupons.service.exception;

public class CouponAlreadyRedeemedException extends CouponException {

    public CouponAlreadyRedeemedException(String code, String userId) {
        super(CouponErrorCode.ALREADY_REDEEMED,
                "User '" + userId + "' has already redeemed coupon '" + code + "'.");
    }
}
