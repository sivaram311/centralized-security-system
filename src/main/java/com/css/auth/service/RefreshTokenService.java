package com.css.auth.service;

import com.css.auth.model.RefreshToken;
import com.css.auth.model.RegisteredApplication;
import com.css.auth.model.UserAccount;
import com.css.auth.repository.RefreshTokenRepository;
import com.css.auth.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final UserAccountService userAccountService;

    @Value("${css.jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               JwtTokenService jwtTokenService,
                               UserAccountService userAccountService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenService = jwtTokenService;
        this.userAccountService = userAccountService;
    }

    @Transactional
    public RefreshToken createRefreshToken(UserAccount user, RegisteredApplication application) {
        refreshTokenRepository.deleteByUserAndApplication(user, application);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setApplication(application);
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusSeconds(refreshExpirationDays * 24L * 60 * 60));
        token.setRevoked(false);
        return refreshTokenRepository.save(token);
    }

    public record RefreshResult(String accessToken, String username, java.util.List<String> roles) {}

    @Transactional
    public RefreshResult refreshAccessTokenWithContext(String refreshTokenValue, String clientId) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.isRevoked() || refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        RegisteredApplication app = userAccountService.requireApplication(clientId);
        if (!refreshToken.getApplication().getClientId().equals(app.getClientId())) {
            throw new IllegalArgumentException("Refresh token does not belong to this client");
        }

        UserAccount user = refreshToken.getUser();
        var roles = userAccountService.rolesForUserAndApp(user, app);
        String accessToken = jwtTokenService.generateAccessToken(user.getUsername(), app.getClientId(), roles);
        return new RefreshResult(accessToken, user.getUsername(), roles);
    }

    @Transactional
    public void revokeForUserAndClient(String username, String clientId) {
        UserAccount user = userAccountService.requireUser(username);
        RegisteredApplication app = userAccountService.requireApplication(clientId);
        refreshTokenRepository.deleteByUserAndApplication(user, app);
    }
}
