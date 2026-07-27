package com.coupons.config;

import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    /**
     * Dedicated {@link RestClient} for the geolocation provider, with tight timeouts so a slow or
     * unavailable third party can never block redeem requests for long.
     */
    @Bean
    RestClient geoRestClient(@org.springframework.beans.factory.annotation.Value("${coupons.geo.base-url:http://ip-api.com}") String baseUrl) {
        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .build();
    }
}
