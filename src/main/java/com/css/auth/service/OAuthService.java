package com.css.auth.service;

import com.css.auth.dto.TokenResponse;
import com.css.auth.model.AuthorizationCode;
import com.css.auth.model.RefreshToken;
import com.css.auth.model.RegisteredApplication;
import com.css.auth.model.UserAccount;
import com.css.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

/**
 * Orchestrates the OIDC-shaped Authorization Code + PKCE flow (ADR 001), reusing
 * the existing password-login building blocks (AuthenticationManager, JwtTokenService,
 * RefreshTokenService, UserAccountService) so issued tokens are identical in shape
 * to the legacy POST /auth/login response.
 */
@Service
public class OAuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountService userAccountService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final AuthorizationCodeService authorizationCodeService;

    @Value("${css.jwt.access-expiration-ms:900000}")
    private long accessExpirationMs;

    public OAuthService(AuthenticationManager authenticationManager,
                         UserAccountService userAccountService,
                         RefreshTokenService refreshTokenService,
                         JwtTokenService jwtTokenService,
                         AuthorizationCodeService authorizationCodeService) {
        this.authenticationManager = authenticationManager;
        this.userAccountService = userAccountService;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenService = jwtTokenService;
        this.authorizationCodeService = authorizationCodeService;
    }

    /** Throws IllegalArgumentException if the client is unknown or disabled. */
    public void requireRegisteredClient(String clientId) {
        userAccountService.requireApplication(clientId);
    }

    /**
     * v1 allow-list: http(s) localhost/127.0.0.1 for local dev, or any https host
     * under the delena.buzz apex for deployed apps. Per-app allowed redirect URIs
     * can be persisted on RegisteredApplication in a later phase.
     */
    public boolean isRedirectUriAllowed(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(redirectUri);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return false;
            }
            if ("http".equalsIgnoreCase(scheme)) {
                return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
            }
            if ("https".equalsIgnoreCase(scheme)) {
                return "localhost".equalsIgnoreCase(host)
                        || host.equalsIgnoreCase("delena.buzz")
                        || host.toLowerCase().endsWith(".delena.buzz");
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Authenticates username/password and returns the canonical username. */
    public String authenticate(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username.trim(), password));
        return authentication.getName();
    }

    /**
     * Issues a code for a user already known to hold roles for the given client.
     * Throws IllegalArgumentException if the client is unknown or the user has no roles for it.
     */
    @Transactional
    public String issueAuthorizationCode(String username, String clientId, String redirectUri,
                                          String codeChallenge, String codeChallengeMethod, String state) {
        RegisteredApplication app = userAccountService.requireApplication(clientId);
        UserAccount user = userAccountService.requireUser(username);
        List<String> roles = userAccountService.rolesForUserAndApp(user, app);
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("User has no roles for application: " + clientId);
        }
        AuthorizationCode authorizationCode = authorizationCodeService.createCode(
                username, clientId, redirectUri, codeChallenge, codeChallengeMethod, state);
        return authorizationCode.getCode();
    }

    /** Exchanges a valid, unconsumed code + matching PKCE verifier for app-scoped tokens. */
    @Transactional
    public TokenResponse exchangeAuthorizationCode(String code, String clientId, String redirectUri, String codeVerifier) {
        AuthorizationCode authorizationCode = authorizationCodeService.consumeAndVerify(code, clientId, redirectUri, codeVerifier);

        RegisteredApplication app = userAccountService.requireApplication(clientId);
        UserAccount user = userAccountService.requireUser(authorizationCode.getUsername());
        List<String> roles = userAccountService.rolesForUserAndApp(user, app);

        String accessToken = jwtTokenService.generateAccessToken(user.getUsername(), app.getClientId(), roles);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, app);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .expiresIn(accessExpirationMs / 1000)
                .username(user.getUsername())
                .clientId(app.getClientId())
                .roles(roles)
                .build();
    }
}
