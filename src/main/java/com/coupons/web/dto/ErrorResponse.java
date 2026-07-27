package com.coupons.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error envelope for all failed requests. {@code code} is a stable, machine-readable
 * identifier of the outcome; {@code details} carries per-field validation messages when relevant.
 */
@Schema(description = "Consistent error envelope returned for any failed request.")
public record ErrorResponse(

        @Schema(description = "When the error was produced (UTC).", example = "2026-07-27T20:53:20.491378763Z")
        Instant timestamp,

        @Schema(description = "HTTP status code.", example = "409")
        int status,

        @Schema(description = "Stable, machine-readable outcome code.", example = "COUPON_EXHAUSTED",
                allowableValues = {"COUPON_NOT_FOUND", "COUPON_EXHAUSTED", "COUNTRY_NOT_ALLOWED",
                        "ALREADY_REDEEMED", "DUPLICATE_CODE", "COUNTRY_UNRESOLVED", "VALIDATION_ERROR",
                        "INTERNAL_ERROR"})
        String code,

        @Schema(description = "Human-readable message.", example = "Coupon 'WIOSNA' has reached its maximum number of uses.")
        String message,

        @Schema(description = "Request path.", example = "/api/v1/coupons/WIOSNA/redemptions")
        String path,

        @Schema(description = "Per-field validation messages, when applicable.")
        List<String> details
) {
    public static ErrorResponse of(int status, String code, String message, String path, List<String> details) {
        return new ErrorResponse(Instant.now(), status, code, message, path, details);
    }
}
