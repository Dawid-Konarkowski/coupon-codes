package com.empik.coupons.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Body of a redeem request. {@code userId} is optional: when provided it enforces the
 * "one redemption per user" rule; when absent the coupon is limited only by its maximum uses.
 */
@Schema(description = "Payload for redeeming a coupon. May be empty ({}) for an anonymous redemption.")
public record RedeemCouponRequest(

        @Schema(description = "Optional user identifier. When present, the coupon can be redeemed only "
                + "once per user. When absent, only the usage limit applies.",
                example = "user-1", nullable = true, maxLength = 128)
        @Size(max = 128, message = "userId must be at most 128 characters")
        String userId
) {
}
