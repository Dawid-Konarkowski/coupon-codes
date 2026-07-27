package com.empik.coupons.web;

import com.empik.coupons.domain.Coupon;
import com.empik.coupons.service.CouponService;
import com.empik.coupons.web.dto.CouponResponse;
import com.empik.coupons.web.dto.CreateCouponRequest;
import com.empik.coupons.web.dto.ErrorResponse;
import com.empik.coupons.web.dto.RedeemCouponRequest;
import com.empik.coupons.web.dto.RedeemCouponResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/coupons")
@Tag(name = "Coupons", description = "Creating discount coupons and registering their redemptions.")
public class CouponController {

    private final CouponService couponService;
    private final ClientCountryResolver countryResolver;

    public CouponController(CouponService couponService, ClientCountryResolver countryResolver) {
        this.couponService = couponService;
        this.countryResolver = countryResolver;
    }

    @PostMapping
    @Operation(
            summary = "Create a coupon",
            description = """
                    Creates a new discount coupon. The code is unique and case-insensitive
                    (`WIOSNA` and `wiosna` are the same coupon) and is stored normalized to upper case.
                    No authentication is required.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Coupon created",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = CouponResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation error (VALIDATION_ERROR)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "A coupon with this code already exists (DUPLICATE_CODE)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<CouponResponse> create(@Valid @RequestBody CreateCouponRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        Coupon coupon = couponService.createCoupon(request.code(), request.maxUses(), request.country());
        URI location = uriBuilder.path("/api/v1/coupons/{code}").buildAndExpand(coupon.getCode()).toUri();
        return ResponseEntity.created(location).body(CouponResponse.from(coupon));
    }

    @PostMapping("/{code}/redemptions")
    @Operation(
            summary = "Redeem a coupon",
            description = """
                    Registers a single use of a coupon. Checks are applied in order and a rejected
                    request has no side effect: the coupon must exist, the caller's country (resolved
                    from the IP) must match the coupon's country, an optional `userId` may be redeemed
                    only once, and the usage limit is consumed atomically ("first come, first served").

                    The caller's country is derived from the `X-Forwarded-For` header (or the remote
                    address). For local testing the `X-Country-Code` header can override it when
                    enabled (property `coupons.geo.allow-country-header`).""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Coupon redeemed",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RedeemCouponResponse.class))),
            @ApiResponse(responseCode = "400", description = "Malformed body / validation error (VALIDATION_ERROR)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Request originates from a disallowed country (COUNTRY_NOT_ALLOWED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Coupon does not exist (COUPON_NOT_FOUND)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Coupon exhausted (COUPON_EXHAUSTED) or already redeemed by this user (ALREADY_REDEEMED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Country could not be resolved from the IP — geolocation unavailable (COUNTRY_UNRESOLVED)",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @Parameters(@Parameter(in = ParameterIn.HEADER, name = "X-Country-Code",
            description = "Optional ISO 3166-1 alpha-2 country override for testing (honored when "
                    + "coupons.geo.allow-country-header is enabled).",
            example = "PL", schema = @Schema(type = "string")))
    public ResponseEntity<RedeemCouponResponse> redeem(
            @Parameter(description = "Coupon code (case-insensitive)", example = "WIOSNA")
            @PathVariable String code,
            @RequestBody(required = false) @Valid RedeemCouponRequest request,
            @Parameter(hidden = true) HttpServletRequest httpRequest) {
        String country = countryResolver.resolveCountry(httpRequest);
        String userId = request == null ? null : request.userId();

        Coupon coupon = couponService.redeem(code, country, userId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(RedeemCouponResponse.of(coupon.getCode(), coupon.getCurrentUses(), coupon.getMaxUses()));
    }
}
