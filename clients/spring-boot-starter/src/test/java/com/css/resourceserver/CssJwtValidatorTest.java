package com.css.resourceserver;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CssJwtValidatorTest {

    private static final String ISSUER = "http://localhost:9000";
    private static final String CLIENT_ID = "my-app";
    private static final String KID = "test-kid";

    private KeyPair keyPair;
    private CssResourceServerProperties properties;
    private CssJwtValidator validator;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        properties = new CssResourceServerProperties();
        properties.setEnabled(true);
        properties.setIssuer(ISSUER);
        properties.setClientId(CLIENT_ID);
        properties.setJwksUri("http://localhost:9000/.well-known/jwks.json");
        properties.setJwksCacheSeconds(3600);

        validator = new CssJwtValidator(properties);
        validator.primeKeyCacheForTests(keyPair.getPublic(), KID, Instant.now().plusSeconds(3600));
    }

    private String buildToken(String audience, List<String> roles, Date expiration) {
        var builder = Jwts.builder()
                .header().add("kid", KID).and()
                .subject("user1")
                .issuer(ISSUER)
                .issuedAt(new Date())
                .expiration(expiration);

        if (audience != null) {
            builder.audience().add(audience).and();
        }
        if (roles != null) {
            builder.claim("roles", roles);
        }

        return builder.signWith(keyPair.getPrivate()).compact();
    }

    private Date inOneHour() {
        return Date.from(Instant.now().plusSeconds(3600));
    }

    @Test
    void authenticate_withValidToken_returnsAuthenticationWithRoles() {
        String token = buildToken(CLIENT_ID, List.of("ROLE_USER", "ROLE_ADMIN"), inOneHour());

        Optional<?> result = validator.authenticate(token);

        assertThat(result).isPresent();
    }

    @Test
    void authenticate_withWrongAudience_returnsEmpty() {
        String token = buildToken("some-other-app", List.of("ROLE_USER"), inOneHour());

        Optional<?> result = validator.authenticate(token);

        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_withExpiredToken_returnsEmpty() {
        Date past = Date.from(Instant.now().minusSeconds(3600));
        String token = buildToken(CLIENT_ID, List.of("ROLE_USER"), past);

        Optional<?> result = validator.authenticate(token);

        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_whenDisabled_returnsEmpty() {
        properties.setEnabled(false);
        String token = buildToken(CLIENT_ID, List.of("ROLE_USER"), inOneHour());

        Optional<?> result = validator.authenticate(token);

        assertThat(result).isEmpty();
    }

    @Test
    void authenticate_withMissingRolesClaim_returnsEmpty() {
        String token = buildToken(CLIENT_ID, null, inOneHour());

        Optional<?> result = validator.authenticate(token);

        assertThat(result).isEmpty();
    }
}
