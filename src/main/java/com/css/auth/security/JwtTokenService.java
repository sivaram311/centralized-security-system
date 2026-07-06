package com.css.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenService {

    private final JwtKeyProvider keyProvider;

    @Value("${css.issuer}")
    private String issuer;

    @Value("${css.jwt.access-expiration-ms:900000}")
    private long accessExpirationMs;

    public JwtTokenService(JwtKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    public String generateAccessToken(String username, String clientId, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().add("kid", keyProvider.getKeyId()).and()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(username)
                .audience().add(clientId).and()
                .claim("roles", roles)
                .claim("client_id", clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessExpirationMs)))
                .signWith(keyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    public Claims parseAndValidate(String token, String expectedClientId) {
        Claims claims = Jwts.parser()
                .verifyWith(keyProvider.getPublicKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (expectedClientId != null) {
            boolean audienceMatch = claims.getAudience().contains(expectedClientId)
                    || expectedClientId.equals(claims.get("client_id", String.class));
            if (!audienceMatch) {
                throw new JwtException("Token audience does not match client");
            }
        }
        return claims;
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(keyProvider.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Map<String, Object> buildJwks() {
        return Map.of(
                "keys", List.of(Map.of(
                        "kty", "RSA",
                        "use", "sig",
                        "alg", "RS256",
                        "kid", keyProvider.getKeyId(),
                        "n", base64Url(keyProvider.getPublicKey().getModulus().toByteArray()),
                        "e", base64Url(keyProvider.getPublicKey().getPublicExponent().toByteArray())
                ))
        );
    }

    private String base64Url(byte[] bytes) {
        // Strip leading zero byte from BigInteger encoding if present
        int offset = 0;
        if (bytes.length > 1 && bytes[0] == 0) {
            offset = 1;
        }
        byte[] trimmed = new byte[bytes.length - offset];
        System.arraycopy(bytes, offset, trimmed, 0, trimmed.length);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(trimmed);
    }
}
