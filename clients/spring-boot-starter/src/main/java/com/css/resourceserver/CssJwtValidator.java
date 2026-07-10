package com.css.resourceserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class CssJwtValidator {

    private static final Logger log = LoggerFactory.getLogger(CssJwtValidator.class);

    private final CssResourceServerProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile PublicKey cachedPublicKey;
    private volatile String cachedKid;
    private volatile Instant cacheExpiresAt = Instant.EPOCH;

    public CssJwtValidator(CssResourceServerProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public Optional<UsernamePasswordAuthenticationToken> authenticate(String token) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            String kid = extractKid(token);
            PublicKey publicKey = resolvePublicKey(kid);
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!audienceMatches(claims)) {
                throw new JwtException("Token audience does not match client");
            }

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            if (roles == null || roles.isEmpty()) {
                throw new JwtException("Token has no roles for this application");
            }

            var authorities = roles.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            return Optional.of(new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities));
        } catch (Exception ex) {
            log.debug("CSS JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean audienceMatches(Claims claims) {
        String clientId = properties.getClientId();
        if (claims.getAudience() != null && claims.getAudience().contains(clientId)) {
            return true;
        }
        return clientId.equals(claims.get("client_id", String.class));
    }

    private String extractKid(String token) {
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(token.split("\\.")[0]));
            JsonNode header = objectMapper.readTree(headerJson);
            return header.path("kid").asText(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private PublicKey resolvePublicKey(String kid) throws Exception {
        if (cachedPublicKey != null && Instant.now().isBefore(cacheExpiresAt)
                && (kid == null || kid.equals(cachedKid))) {
            return cachedPublicKey;
        }
        synchronized (this) {
            if (cachedPublicKey != null && Instant.now().isBefore(cacheExpiresAt)
                    && (kid == null || kid.equals(cachedKid))) {
                return cachedPublicKey;
            }
            JsonNode jwks = restTemplate.getForObject(properties.getJwksUri(), JsonNode.class);
            if (jwks == null || !jwks.has("keys")) {
                throw new IllegalStateException("JWKS response missing keys");
            }
            JsonNode keyNode = selectKey(jwks.get("keys"), kid);
            if (keyNode == null) {
                throw new IllegalStateException("No matching JWK for kid=" + kid);
            }
            cachedPublicKey = toPublicKey(keyNode);
            cachedKid = keyNode.path("kid").asText(null);
            cacheExpiresAt = Instant.now().plusSeconds(properties.getJwksCacheSeconds());
            return cachedPublicKey;
        }
    }

    private JsonNode selectKey(JsonNode keys, String kid) {
        if (kid != null) {
            for (JsonNode key : keys) {
                if (kid.equals(key.path("kid").asText(null))) {
                    return key;
                }
            }
        }
        return keys.isEmpty() ? null : keys.get(0);
    }

    private PublicKey toPublicKey(JsonNode keyNode) throws Exception {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(keyNode.get("n").asText());
        byte[] exponentBytes = Base64.getUrlDecoder().decode(keyNode.get("e").asText());
        RSAPublicKeySpec spec = new RSAPublicKeySpec(
                new BigInteger(1, modulusBytes),
                new BigInteger(1, exponentBytes)
        );
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }
}
