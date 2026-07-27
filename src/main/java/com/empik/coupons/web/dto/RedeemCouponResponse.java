package com.empik.coupons.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a successful coupon redemption.")
public record RedeemCouponResponse(

        @Schema(description = "Coupon code.", example = "WIOSNA")
        String code,

        @Schema(description = "Human-readable outcome message.", example = "Coupon redeemed successfully.")
        String message,

        @Schema(description = "Number of uses after this redemption.", example = "1")
        int currentUses,

        @Schema(description = "Maximum number of uses.", example = "100")
        int maxUses,

        @Schema(description = "Uses still available.", example = "99")
        int remainingUses
) {
    public static RedeemCouponResponse of(String code, int currentUses, int maxUses) {
        return new RedeemCouponResponse(
                code,
                "Coupon redeemed successfully.",
                currentUses,
                maxUses,
                Math.max(0, maxUses - currentUses)
        );
    }
}
