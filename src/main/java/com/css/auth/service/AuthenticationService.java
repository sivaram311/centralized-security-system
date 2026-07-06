package com.css.auth.service;

import com.css.auth.dto.LoginRequest;
import com.css.auth.dto.RefreshTokenRequest;
import com.css.auth.dto.TokenResponse;
import com.css.auth.model.RegisteredApplication;
import com.css.auth.model.UserAccount;
import com.css.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountService userAccountService;
    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;

    @Value("${css.jwt.access-expiration-ms:900000}")
    private long accessExpirationMs;

    public AuthenticationService(AuthenticationManager authenticationManager,
                                 UserAccountService userAccountService,
                                 RefreshTokenService refreshTokenService,
                                 JwtTokenService jwtTokenService) {
        this.authenticationManager = authenticationManager;
        this.userAccountService = userAccountService;
        this.refreshTokenService = refreshTokenService;
        this.jwtTokenService = jwtTokenService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        RegisteredApplication app = userAccountService.requireApplication(request.getClientId());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername().trim(), request.getPassword())
        );

        UserAccount user = userAccountService.requireUser(authentication.getName());
        var roles = userAccountService.rolesForUserAndApp(user, app);

        if (roles.isEmpty()) {
            throw new IllegalArgumentException("User has no roles for application: " + app.getClientId());
        }

        String accessToken = jwtTokenService.generateAccessToken(user.getUsername(), app.getClientId(), roles);
        var refreshToken = refreshTokenService.createRefreshToken(user, app);

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

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshTokenRequest request) {
        RegisteredApplication app = userAccountService.requireApplication(request.getClientId());
        var refreshResult = refreshTokenService.refreshAccessTokenWithContext(
                request.getRefreshToken(), app.getClientId());

        return TokenResponse.builder()
                .accessToken(refreshResult.accessToken())
                .tokenType("Bearer")
                .expiresIn(accessExpirationMs / 1000)
                .username(refreshResult.username())
                .clientId(app.getClientId())
                .roles(refreshResult.roles())
                .build();
    }
}
