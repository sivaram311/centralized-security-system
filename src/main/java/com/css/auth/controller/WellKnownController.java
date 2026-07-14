package com.css.auth.controller;

import com.css.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WellKnownController {

    private final JwtTokenService jwtTokenService;
    private final String issuer;

    public WellKnownController(
            JwtTokenService jwtTokenService,
            @Value("${css.issuer}") String issuer) {
        this.jwtTokenService = jwtTokenService;
        this.issuer = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
    }

    /** JWKS endpoint — downstream apps fetch public keys to validate RS256 tokens */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwtTokenService.buildJwks();
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> openIdConfiguration() {
        return Map.of(
                "issuer", issuer,
                "jwks_uri", issuer + "/.well-known/jwks.json",
                "token_endpoint", issuer + "/auth/login",
                "grant_types_supported", new String[]{"password", "refresh_token"},
                "id_token_signing_alg_values_supported", new String[]{"RS256"}
        );
    }
}
