package com.coupons.service.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;

/**
 * Geolocation backed by the free <a href="http://ip-api.com">ip-api.com</a> service
 * (no API key, rate-limited to ~45 requests/min from a single IP).
 *
 * <p>Private, loopback and otherwise non-public addresses cannot be geolocated. For those we fall
 * back to a configurable default country ({@code coupons.geo.default-country}) which keeps the
 * service usable in local/containerized environments where the caller's IP is not routable.
 */
@Service
public class IpApiGeoLocationService implements GeoLocationService {

    private static final Logger log = LoggerFactory.getLogger(IpApiGeoLocationService.class);

    private final RestClient restClient;
    private final String defaultCountryForLocalAddresses;

    public IpApiGeoLocationService(RestClient geoRestClient,
                                   org.springframework.core.env.Environment env) {
        this.restClient = geoRestClient;
        this.defaultCountryForLocalAddresses =
                env.getProperty("coupons.geo.default-country", "").trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public Optional<String> resolveCountry(String ip) {
        if (!StringUtils.hasText(ip)) {
            return Optional.empty();
        }
        if (isNonPublic(ip)) {
            log.debug("IP {} is non-public; using default country '{}'", ip, defaultCountryForLocalAddresses);
            return defaultCountryForLocalAddresses.isEmpty()
                    ? Optional.empty()
                    : Optional.of(defaultCountryForLocalAddresses);
        }
        return queryProvider(ip);
    }

    private Optional<String> queryProvider(String ip) {
        try {
            IpApiResponse response = restClient.get()
                    .uri("/json/{ip}?fields=status,countryCode", ip)
                    .retrieve()
                    .body(IpApiResponse.class);

            if (response != null && "success".equalsIgnoreCase(response.status())
                    && StringUtils.hasText(response.countryCode())) {
                return Optional.of(response.countryCode().toUpperCase(Locale.ROOT));
            }
            log.warn("Geolocation for IP {} returned no usable result: {}", ip, response);
            return Optional.empty();
        } catch (Exception ex) {
            // Never let a geolocation outage crash the request; treat as "unresolved".
            log.warn("Geolocation lookup for IP {} failed: {}", ip, ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean isNonPublic(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.isLoopbackAddress()
                    || addr.isAnyLocalAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Minimal projection of the ip-api.com JSON response. */
    private record IpApiResponse(String status, String countryCode) {
    }
}
