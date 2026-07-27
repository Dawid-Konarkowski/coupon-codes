package com.coupons.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponTest {

    @Test
    void normalizesCodeAndCountryToUpperCaseOnCreation() {
        Coupon coupon = new Coupon("  wiosna ", 3, "pl");

        assertThat(coupon.getCode()).isEqualTo("WIOSNA");
        assertThat(coupon.getCountry()).isEqualTo("PL");
    }

    @Test
    void isForCountryIsCaseInsensitive() {
        Coupon coupon = new Coupon("WIOSNA", 3, "PL");

        assertThat(coupon.isForCountry("pl")).isTrue();
        assertThat(coupon.isForCountry("PL")).isTrue();
        assertThat(coupon.isForCountry("de")).isFalse();
    }

    @Test
    void isExhaustedReflectsUsage() {
        Coupon coupon = new Coupon("WIOSNA", 1, "PL");

        assertThat(coupon.isExhausted()).isFalse();
    }

    @Test
    void normalizeHelpersHandleNull() {
        assertThat(Coupon.normalizeCode(null)).isNull();
        assertThat(Coupon.normalizeCountry(null)).isNull();
        assertThat(Coupon.normalizeCode(" spring ")).isEqualTo("SPRING");
    }
}
