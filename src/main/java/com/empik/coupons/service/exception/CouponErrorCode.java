package com.empik.coupons.service.exception;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable outcomes returned to the client, each mapped to a stable HTTP status.
 * Kept in one place so the API contract for error responses is explicit and consistent.
 */
public enum CouponErrorCode {

    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND),
    COUPON_EXHAUSTED(HttpStatus.CONFLICT),
    COUNTRY_NOT_ALLOWED(HttpStatus.FORBIDDEN),
    ALREADY_REDEEMED(HttpStatus.CONFLICT),
    DUPLICATE_CODE(HttpStatus.CONFLICT),
    // Resolving the country from the IP failed on the server side (geolocation provider unavailable
    // or the address is non-routable) — a transient/server condition, not a client content error.
    COUNTRY_UNRESOLVED(HttpStatus.SERVICE_UNAVAILABLE),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST);

    private final HttpStatus status;

    CouponErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
