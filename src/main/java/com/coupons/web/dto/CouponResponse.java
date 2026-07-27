package com.coupons.web.dto;

import com.coupons.domain.Coupon;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A coupon and its current usage state.")
public record CouponResponse(

        @Schema(description = "Internal identifier.", example = "3f1e9c7a-1b2c-4d5e-8f90-1234567890ab")
        UUID id,

        @Schema(description = "Coupon code (normalized to upper case).", example = "WIOSNA")
        String code,

        @Schema(description = "Creation timestamp (UTC).", example = "2026-07-27T20:53:20.340456053Z")
        Instant createdAt,

        @Schema(description = "Maximum number of uses.", example = "100")
        int maxUses,

        @Schema(description = "Number of uses already registered.", example = "0")
        int currentUses,

        @Schema(description = "ISO 3166-1 alpha-2 country the coupon is valid in.", example = "PL")
        String country
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getCreatedAt(),
                coupon.getMaxUses(),
                coupon.getCurrentUses(),
                coupon.getCountry()
        );
    }
}
