package com.coupons.service.geo;

import java.util.Optional;

/**
 * Resolves the ISO 3166-1 alpha-2 country code for a client IP address.
 * Abstracted behind an interface so the geolocation provider can be swapped or stubbed in tests.
 */
public interface GeoLocationService {

    Optional<String> resolveCountry(String ip);
}
