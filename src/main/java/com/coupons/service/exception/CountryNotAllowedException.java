package com.coupons.service.exception;

public class CountryNotAllowedException extends CouponException {

    public CountryNotAllowedException(String code, String requestCountry, String allowedCountry) {
        super(CouponErrorCode.COUNTRY_NOT_ALLOWED,
                "Coupon '" + code + "' is only valid in '" + allowedCountry
                        + "', but the request originates from '" + requestCountry + "'.");
    }
}
