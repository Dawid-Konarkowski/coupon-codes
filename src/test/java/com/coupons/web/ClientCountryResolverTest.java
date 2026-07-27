package com.coupons.web;

import com.coupons.service.exception.CountryNotResolvableException;
import com.coupons.service.geo.GeoLocationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientCountryResolverTest {

    private final GeoLocationService geo = mock(GeoLocationService.class);

    @Test
    void headerOverrideWinsWhenAllowed() {
        ClientCountryResolver resolver = new ClientCountryResolver(geo, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Country-Code", "pl");

        assertThat(resolver.resolveCountry(request)).isEqualTo("PL");
        verify(geo, never()).resolveCountry(any());
    }

    @Test
    void headerOverrideIgnoredWhenDisabled() {
        ClientCountryResolver resolver = new ClientCountryResolver(geo, false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Country-Code", "DE");
        request.setRemoteAddr("8.8.8.8");
        when(geo.resolveCountry("8.8.8.8")).thenReturn(Optional.of("US"));

        assertThat(resolver.resolveCountry(request)).isEqualTo("US");
    }

    @Test
    void usesFirstHopFromForwardedForHeader() {
        ClientCountryResolver resolver = new ClientCountryResolver(geo, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8");
        when(geo.resolveCountry("1.2.3.4")).thenReturn(Optional.of("PL"));

        assertThat(resolver.resolveCountry(request)).isEqualTo("PL");
    }

    @Test
    void fallsBackToRemoteAddress() {
        ClientCountryResolver resolver = new ClientCountryResolver(geo, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("9.9.9.9");
        when(geo.resolveCountry("9.9.9.9")).thenReturn(Optional.of("DE"));

        assertThat(resolver.resolveCountry(request)).isEqualTo("DE");
    }

    @Test
    void throwsWhenCountryCannotBeResolved() {
        ClientCountryResolver resolver = new ClientCountryResolver(geo, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("9.9.9.9");
        when(geo.resolveCountry("9.9.9.9")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveCountry(request))
                .isInstanceOf(CountryNotResolvableException.class);
    }
}
