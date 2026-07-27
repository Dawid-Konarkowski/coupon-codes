package com.coupons.web.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedeemCouponResponseTest {

    @Test
    void computesRemainingUses() {
        RedeemCouponResponse response = RedeemCouponResponse.of("WIOSNA", 1, 5);

        assertThat(response.remainingUses()).isEqualTo(4);
    }

    @Test
    void remainingUsesNeverGoesNegative() {
        RedeemCouponResponse response = RedeemCouponResponse.of("WIOSNA", 5, 5);

        assertThat(response.remainingUses()).isZero();
    }
}
