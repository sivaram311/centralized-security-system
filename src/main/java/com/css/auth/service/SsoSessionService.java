package com.css.auth.service;

import com.css.auth.model.SsoSession;
import com.css.auth.repository.SsoSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages the DB-backed SSO session that backs the browser cookie for the
 * OIDC-shaped Authorization Code + PKCE flow. This is intentionally separate
 * from Spring Security's (stateless) session handling.
 */
@Service
public class SsoSessionService {

    private final SsoSessionRepository ssoSessionRepository;

    @Value("${css.oauth.sso-session-expiration-hours:8}")
    private int sessionExpirationHours;

    @Value("${css.oauth.cookie-name:CSS_SSO}")
    private String cookieName;

    @Value("${css.oauth.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${css.oauth.cookie-same-site:Lax}")
    private String cookieSameSite;

    public SsoSessionService(SsoSessionRepository ssoSessionRepository) {
        this.ssoSessionRepository = ssoSessionRepository;
    }

    @Transactional
    public SsoSession createSession(String username) {
        SsoSession session = new SsoSession();
        session.setSessionToken(UUID.randomUUID().toString());
        session.setUsername(username);
        session.setExpiresAt(Instant.now().plusSeconds(sessionExpirationHours * 3600L));
        session.setRevoked(false);
        return ssoSessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public Optional<String> validate(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        return ssoSessionRepository.findBySessionToken(sessionToken)
                .filter(s -> !s.isRevoked() && s.getExpiresAt().isAfter(Instant.now()))
                .map(SsoSession::getUsername);
    }

    @Transactional
    public void revoke(String sessionToken) {
        if (sessionToken == null) {
            return;
        }
        ssoSessionRepository.findBySessionToken(sessionToken).ifPresent(session -> {
            session.setRevoked(true);
            ssoSessionRepository.save(session);
        });
    }

    public String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public ResponseCookie buildCookie(String sessionToken) {
        return ResponseCookie.from(cookieName, sessionToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.ofHours(sessionExpirationHours))
                .build();
    }

    public ResponseCookie buildExpiredCookie() {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(0)
                .build();
    }

    public String getCookieName() {
        return cookieName;
    }
}
