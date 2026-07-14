package com.css.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.test.context.ActiveProfiles;

/**
 * Integration coverage for the JWKS discovery endpoint and the RS256 claims issued
 * by the login flow. Runs against a random port with the real H2 + DataSeeder setup,
 * so tokens are verified with the public key served by the app's own JWKS endpoint
 * rather than any key reconstructed from internals.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class JwtClaimsAndJwksIT {

    private static final String CLIENT_ID = "agent-portal";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    @Autowired
    private TestRestTemplate restTemplate;

    /** Issuer is a fixed config value (css.issuer), independent of the RANDOM_PORT the test server binds to. */
    @Value("${css.issuer}")
    private String configuredIssuer;

    @Test
    @SuppressWarnings("unchecked")
    void jwksEndpoint_returnsRsaKeySet() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/.well-known/jwks.json", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull().containsKey("keys");

        List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");
        assertThat(keys).isNotEmpty();

        Map<String, Object> key = keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key.get("use")).isEqualTo("sig");
        assertThat(key.get("alg")).isEqualTo("RS256");
        assertThat(key).containsKeys("kid", "n", "e");
    }

    @Test
    void login_tokenSignature_verifiesAgainstJwksPublicKey() throws Exception {
        String accessToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, CLIENT_ID);
        RSAPublicKey publicKey = fetchJwksPublicKey();

        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(ADMIN_USERNAME);
    }

    @Test
    void login_tokenClaims_issuerMatchesConfiguredCssIssuer() throws Exception {
        String accessToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, CLIENT_ID);
        RSAPublicKey publicKey = fetchJwksPublicKey();

        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(configuredIssuer)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

        assertThat(claims.getIssuer()).isEqualTo(configuredIssuer);
    }

    @Test
    void login_tokenClaims_containRolesForAgentPortal() throws Exception {
        String accessToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, CLIENT_ID);
        RSAPublicKey publicKey = fetchJwksPublicKey();

        Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(accessToken).getPayload();

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertThat(roles).isNotNull().contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void login_tokenClaims_audienceContainsAgentPortalClientId() throws Exception {
        String accessToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, CLIENT_ID);
        RSAPublicKey publicKey = fetchJwksPublicKey();

        Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(accessToken).getPayload();

        // aud may be encoded by jjwt as a single string or a JSON array; getAudience() normalizes both.
        assertThat(claims.getAudience()).contains(CLIENT_ID);
        assertThat(claims.get("client_id", String.class)).isEqualTo(CLIENT_ID);
    }

    @Test
    void tamperedToken_failsSignatureVerification() throws Exception {
        String accessToken = login(ADMIN_USERNAME, ADMIN_PASSWORD, CLIENT_ID);
        RSAPublicKey publicKey = fetchJwksPublicKey();

        String tampered = tamperSignature(accessToken);

        assertThrows(JwtException.class, () ->
                Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(tampered));
    }

    private String login(String username, String password, String clientId) {
        Map<String, String> request = Map.of(
                "username", username,
                "password", password,
                "clientId", clientId
        );

        ResponseEntity<Map> response = restTemplate.postForEntity("/auth/login", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull().containsKey("accessToken");
        return (String) body.get("accessToken");
    }

    @SuppressWarnings("unchecked")
    private RSAPublicKey fetchJwksPublicKey() throws Exception {
        ResponseEntity<Map> response = restTemplate.getForEntity("/.well-known/jwks.json", Map.class);
        Map<String, Object> body = response.getBody();
        List<Map<String, Object>> keys = (List<Map<String, Object>>) body.get("keys");
        Map<String, Object> key = keys.get(0);

        BigInteger modulus = new BigInteger(1, base64UrlDecode((String) key.get("n")));
        BigInteger exponent = new BigInteger(1, base64UrlDecode((String) key.get("e")));

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    /** Flips one character in the signature segment so verification must fail. */
    private String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char[] chars = parts[2].toCharArray();
        int idx = chars.length / 2;
        chars[idx] = chars[idx] == 'a' ? 'b' : 'a';
        parts[2] = new String(chars);
        return parts[0] + "." + parts[1] + "." + parts[2];
    }
}
