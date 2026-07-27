package com.empik.coupons.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a coupon.")
public record CreateCouponRequest(

        @Schema(description = "Unique coupon code; case-insensitive, stored upper-cased.",
                example = "WIOSNA", maxLength = 64)
        @NotBlank(message = "code must not be blank")
        @Size(max = 64, message = "code must be at most 64 characters")
        String code,

        @Schema(description = "Maximum number of times the coupon can be used.", example = "100", minimum = "1")
        @Min(value = 1, message = "maxUses must be at least 1")
        int maxUses,

        @Schema(description = "ISO 3166-1 alpha-2 country the coupon is valid in.", example = "PL")
        @NotBlank(message = "country must not be blank")
        @Pattern(regexp = "^[A-Za-z]{2}$", message = "country must be an ISO 3166-1 alpha-2 code (e.g. PL)")
        String country
) {
}
