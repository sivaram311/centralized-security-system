package com.css.auth.service;

import com.css.auth.model.AuthorizationCode;
import com.css.auth.repository.AuthorizationCodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Issues and consumes short-lived authorization codes for the PKCE flow.
 * PKCE verification per RFC 7636: code_challenge = BASE64URL(SHA256(code_verifier)), no padding.
 */
@Service
public class AuthorizationCodeService {

    private final AuthorizationCodeRepository authorizationCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${css.oauth.code-expiration-seconds:120}")
    private int codeExpirationSeconds;

    public AuthorizationCodeService(AuthorizationCodeRepository authorizationCodeRepository) {
        this.authorizationCodeRepository = authorizationCodeRepository;
    }

    @Transactional
    public AuthorizationCode createCode(String username, String clientId, String redirectUri,
                                         String codeChallenge, String codeChallengeMethod, String state) {
        AuthorizationCode authorizationCode = new AuthorizationCode();
        authorizationCode.setCode(generateCode());
        authorizationCode.setUsername(username);
        authorizationCode.setClientId(clientId);
        authorizationCode.setRedirectUri(redirectUri);
        authorizationCode.setCodeChallenge(codeChallenge);
        authorizationCode.setCodeChallengeMethod(codeChallengeMethod != null ? codeChallengeMethod : "S256");
        authorizationCode.setExpiresAt(Instant.now().plusSeconds(codeExpirationSeconds));
        authorizationCode.setConsumed(false);
        authorizationCode.setState(state);
        return authorizationCodeRepository.save(authorizationCode);
    }

    /**
     * Consumes (marks used) a code after verifying client, redirect_uri and PKCE
     * challenge. Throws IllegalArgumentException on any validation failure.
     */
    @Transactional
    public AuthorizationCode consumeAndVerify(String code, String clientId, String redirectUri, String codeVerifier) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Missing authorization code");
        }
        AuthorizationCode authorizationCode = authorizationCodeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Invalid authorization code"));

        if (authorizationCode.isConsumed()) {
            throw new IllegalArgumentException("Authorization code already used");
        }
        if (authorizationCode.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Authorization code expired");
        }
        if (!authorizationCode.getClientId().equals(clientId)) {
            throw new IllegalArgumentException("Authorization code does not belong to this client");
        }
        if (redirectUri != null && !redirectUri.isBlank() && !redirectUri.equals(authorizationCode.getRedirectUri())) {
            throw new IllegalArgumentException("redirect_uri mismatch");
        }
        if (!verifyPkce(codeVerifier, authorizationCode.getCodeChallenge())) {
            throw new IllegalArgumentException("Invalid code_verifier");
        }

        authorizationCode.setConsumed(true);
        return authorizationCodeRepository.save(authorizationCode);
    }

    private boolean verifyPkce(String codeVerifier, String expectedChallenge) {
        if (codeVerifier == null || codeVerifier.isBlank() || expectedChallenge == null) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            String computedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return computedChallenge.equals(expectedChallenge);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
