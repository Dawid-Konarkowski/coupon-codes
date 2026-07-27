package com.coupons.service.geo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for the offline decision logic (non-public addresses / default country). The remote
 * ip-api.com lookup for public addresses is intentionally not exercised here — the {@link RestClient}
 * is a mock and must never be called for these inputs.
 */
class IpApiGeoLocationServiceTest {

    private final RestClient restClient = mock(RestClient.class);

    private IpApiGeoLocationService serviceWithDefault(String defaultCountry) {
        MockEnvironment env = new MockEnvironment();
        if (defaultCountry != null) {
            env.setProperty("coupons.geo.default-country", defaultCountry);
        }
        return new IpApiGeoLocationService(restClient, env);
    }

    @Test
    void loopbackAddressResolvesToConfiguredDefaultWithoutCallingProvider() {
        IpApiGeoLocationService service = serviceWithDefault("pl");

        assertThat(service.resolveCountry("127.0.0.1")).contains("PL");
        verifyNoInteractions(restClient);
    }

    @Test
    void privateAddressResolvesToConfiguredDefault() {
        IpApiGeoLocationService service = serviceWithDefault("PL");

        assertThat(service.resolveCountry("10.1.2.3")).contains("PL");
        assertThat(service.resolveCountry("192.168.0.10")).contains("PL");
    }

    @Test
    void nonPublicAddressWithoutDefaultIsUnresolved() {
        IpApiGeoLocationService service = serviceWithDefault(null);

        assertThat(service.resolveCountry("127.0.0.1")).isEmpty();
        verifyNoInteractions(restClient);
    }

    @Test
    void blankIpIsUnresolved() {
        IpApiGeoLocationService service = serviceWithDefault("PL");

        assertThat(service.resolveCountry("  ")).isEmpty();
        verifyNoInteractions(restClient);
    }
}
