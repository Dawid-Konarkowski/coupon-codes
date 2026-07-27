package com.empik.coupons.service.exception;

/**
 * Base type for all expected, domain-level coupon errors. Carrying a {@link CouponErrorCode} lets the
 * web layer translate any of them into a consistent error response without a large switch statement.
 */
public class CouponException extends RuntimeException {

    private final CouponErrorCode errorCode;

    public CouponException(CouponErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CouponErrorCode getErrorCode() {
        return errorCode;
    }
}
