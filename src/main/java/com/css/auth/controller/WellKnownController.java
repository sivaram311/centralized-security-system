package com.css.auth.controller;

import com.css.auth.security.JwtTokenService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class WellKnownController {

    private final JwtTokenService jwtTokenService;

    public WellKnownController(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    /** JWKS endpoint — downstream apps fetch public keys to validate RS256 tokens */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwtTokenService.buildJwks();
    }

    @GetMapping("/.well-known/openid-configuration")
    public Map<String, Object> openIdConfiguration() {
        return Map.of(
                "issuer", "http://localhost:9000",
                "jwks_uri", "http://localhost:9000/.well-known/jwks.json",
                "token_endpoint", "http://localhost:9000/auth/login",
                "grant_types_supported", new String[]{"password", "refresh_token"},
                "id_token_signing_alg_values_supported", new String[]{"RS256"}
        );
    }
}
