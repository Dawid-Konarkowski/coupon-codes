package com.empik.coupons.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level OpenAPI document metadata. Per-endpoint and per-model documentation lives as annotations
 * next to the code it describes (controller and DTOs), so the contract stays in sync with the source.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI couponServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Coupon Service API")
                        .version("v1")
                        .description("""
                                REST API for managing discount coupons: creating coupons and registering
                                their redemptions, with a usage limit ("first come, first served"), a
                                per-country restriction based on the caller's IP, and an optional
                                one-redemption-per-user rule. Designed to behave correctly under
                                concurrency.""")
                        .contact(new Contact().name("Empik Recruitment Task"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}
