package com.empik.coupons.service.exception;

public class CountryNotResolvableException extends CouponException {

    public CountryNotResolvableException(String ip) {
        super(CouponErrorCode.COUNTRY_UNRESOLVED,
                "Could not determine the country of origin for IP address '" + ip + "'.");
    }
}
