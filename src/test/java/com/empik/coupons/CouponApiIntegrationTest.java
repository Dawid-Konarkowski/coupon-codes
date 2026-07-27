package com.empik.coupons;

import com.empik.coupons.repository.CouponRedemptionRepository;
import com.empik.coupons.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CouponApiIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    CouponRepository couponRepository;
    @Autowired
    CouponRedemptionRepository redemptionRepository;

    @BeforeEach
    void cleanUp() {
        redemptionRepository.deleteAll();
        couponRepository.deleteAll();
    }

    @Test
    void createsCouponAndReturnsIt() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"WIOSNA","maxUses":2,"country":"PL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("WIOSNA"))
                .andExpect(jsonPath("$.maxUses").value(2))
                .andExpect(jsonPath("$.currentUses").value(0))
                .andExpect(jsonPath("$.country").value("PL"));
    }

    @Test
    void rejectsDuplicateCodeCaseInsensitively() throws Exception {
        createCoupon("WIOSNA", 5, "PL");

        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"wiosna","maxUses":5,"country":"PL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    void redeemsCouponCaseInsensitively() throws Exception {
        createCoupon("WIOSNA", 1, "PL");

        mockMvc.perform(post("/api/v1/coupons/{code}/redemptions", "wiosna")
                        .header("X-Country-Code", "PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUses").value(1))
                .andExpect(jsonPath("$.remainingUses").value(0));
    }

    @Test
    void returnsNotFoundForUnknownCode() throws Exception {
        mockMvc.perform(post("/api/v1/coupons/{code}/redemptions", "DOESNOTEXIST")
                        .header("X-Country-Code", "PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COUPON_NOT_FOUND"));
    }

    @Test
    void rejectsRedemptionFromDisallowedCountry() throws Exception {
        createCoupon("PLONLY", 5, "PL");

        mockMvc.perform(post("/api/v1/coupons/{code}/redemptions", "PLONLY")
                        .header("X-Country-Code", "DE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNTRY_NOT_ALLOWED"));
    }

    @Test
    void rejectsRedemptionWhenExhausted() throws Exception {
        createCoupon("ONESHOT", 1, "PL");
        redeem("ONESHOT", "PL", null);

        mockMvc.perform(post("/api/v1/coupons/{code}/redemptions", "ONESHOT")
                        .header("X-Country-Code", "PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUPON_EXHAUSTED"));
    }

    @Test
    void rejectsSecondRedemptionBySameUser() throws Exception {
        createCoupon("ONEPERUSER", 5, "PL");
        redeem("ONEPERUSER", "PL", "user-1");

        mockMvc.perform(post("/api/v1/coupons/{code}/redemptions", "ONEPERUSER")
                        .header("X-Country-Code", "PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REDEEMED"));
    }

    @Test
    void rejectsInvalidCreatePayload() throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"","maxUses":0,"country":"POLAND"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMalformedJsonBody() throws Exception {
        createCoupon("MALFORMED", 5, "PL");

        mockMvc.perform(post("/api/v1/coupons/{code}/redemptions", "MALFORMED")
                        .header("X-Country-Code", "PL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void createCoupon(String code, int maxUses, String country) throws Exception {
        mockMvc.perform(post("/api/v1/coupons")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\",\"maxUses\":%d,\"country\":\"%s\"}".formatted(code, maxUses, country)))
                .andExpect(status().isCreated());
    }

    private void redeem(String code, String country, String userId) throws Exception {
        String body = userId == null ? "{}" : "{\"userId\":\"%s\"}".formatted(userId);
        mockMvc.perform(post("/api/v1/coupons/{code}/redemptions", code)
                        .header("X-Country-Code", country)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
