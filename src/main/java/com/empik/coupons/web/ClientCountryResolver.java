package com.empik.coupons.web;

import com.empik.coupons.service.exception.CountryNotResolvableException;
import com.empik.coupons.service.geo.GeoLocationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Determines the country a redeem request originates from.
 *
 * <p>The client IP is taken from {@code X-Forwarded-For} (first hop) when present — the usual case
 * behind a proxy/load balancer — otherwise from the socket remote address. The country is then
 * resolved via {@link GeoLocationService}.
 *
 * <p>For local development and testing an explicit {@code X-Country-Code} header may be honored
 * (guarded by {@code coupons.geo.allow-country-header}); this makes the country-restriction rule
 * fully exercisable without depending on a real, routable client IP or the external provider.
 */
@Component
public class ClientCountryResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String COUNTRY_HEADER = "X-Country-Code";

    private final GeoLocationService geoLocationService;
    private final boolean allowCountryHeader;

    public ClientCountryResolver(GeoLocationService geoLocationService,
                                 @Value("${coupons.geo.allow-country-header:true}") boolean allowCountryHeader) {
        this.geoLocationService = geoLocationService;
        this.allowCountryHeader = allowCountryHeader;
    }

    public String resolveCountry(HttpServletRequest request) {
        if (allowCountryHeader) {
            String override = request.getHeader(COUNTRY_HEADER);
            if (StringUtils.hasText(override)) {
                return override.trim().toUpperCase(Locale.ROOT);
            }
        }
        String ip = extractClientIp(request);
        return geoLocationService.resolveCountry(ip)
                .orElseThrow(() -> new CountryNotResolvableException(ip));
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            // X-Forwarded-For: client, proxy1, proxy2 — the originating client is the first entry.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
