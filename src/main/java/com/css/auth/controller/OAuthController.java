package com.css.auth.controller;

import com.css.auth.dto.TokenExchangeRequest;
import com.css.auth.dto.TokenResponse;
import com.css.auth.model.SsoSession;
import com.css.auth.service.OAuthService;
import com.css.auth.service.SsoSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

/**
 * OIDC-shaped Authorization Code + PKCE endpoints (ADR 001, Phase 2).
 * Existing password-based /auth/login remains untouched for backward compatibility.
 */
@RestController
@RequestMapping("/oauth")
public class OAuthController {

    private final OAuthService oAuthService;
    private final SsoSessionService ssoSessionService;

    public OAuthController(OAuthService oAuthService, SsoSessionService ssoSessionService) {
        this.oAuthService = oAuthService;
        this.ssoSessionService = ssoSessionService;
    }

    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(
            @RequestParam(name = "response_type", defaultValue = "code") String responseType,
            @RequestParam(name = "client_id") String clientId,
            @RequestParam(name = "redirect_uri") String redirectUri,
            @RequestParam(name = "code_challenge") String codeChallenge,
            @RequestParam(name = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam(name = "state", required = false) String state,
            HttpServletRequest request) {

        if (!"code".equals(responseType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_response_type"));
        }
        try {
            oAuthService.requireRegisteredClient(clientId);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_client"));
        }
        if (!oAuthService.isRedirectUriAllowed(redirectUri)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_redirect_uri"));
        }

        String ssoToken = ssoSessionService.extractToken(request);
        Optional<String> username = ssoSessionService.validate(ssoToken);

        if (username.isPresent()) {
            try {
                String code = oAuthService.issueAuthorizationCode(
                        username.get(), clientId, redirectUri, codeChallenge, codeChallengeMethod, state);
                URI location = buildRedirectUri(redirectUri, code, state);
                return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
            } catch (Exception ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "access_denied", "message", ex.getMessage()));
            }
        }

        URI loginUri = buildLoginUri(clientId, redirectUri, codeChallenge, codeChallengeMethod, state, null);
        return ResponseEntity.status(HttpStatus.FOUND).location(loginUri).build();
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> loginForm(
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "code_challenge", required = false) String codeChallenge,
            @RequestParam(name = "code_challenge_method", required = false, defaultValue = "S256") String codeChallengeMethod,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error) {

        String html = renderLoginPage(clientId, redirectUri, codeChallenge, codeChallengeMethod, state, error);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> login(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(name = "client_id") String clientId,
            @RequestParam(name = "redirect_uri") String redirectUri,
            @RequestParam(name = "code_challenge") String codeChallenge,
            @RequestParam(name = "code_challenge_method", defaultValue = "S256") String codeChallengeMethod,
            @RequestParam(name = "state", required = false) String state) {

        try {
            oAuthService.requireRegisteredClient(clientId);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_client"));
        }
        if (!oAuthService.isRedirectUriAllowed(redirectUri)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_redirect_uri"));
        }

        String authenticatedUsername;
        try {
            authenticatedUsername = oAuthService.authenticate(username, password);
        } catch (Exception ex) {
            URI backToLogin = buildLoginUri(clientId, redirectUri, codeChallenge, codeChallengeMethod, state, "invalid_credentials");
            return ResponseEntity.status(HttpStatus.FOUND).location(backToLogin).build();
        }

        try {
            String code = oAuthService.issueAuthorizationCode(
                    authenticatedUsername, clientId, redirectUri, codeChallenge, codeChallengeMethod, state);

            SsoSession session = ssoSessionService.createSession(authenticatedUsername);
            ResponseCookie cookie = ssoSessionService.buildCookie(session.getSessionToken());
            URI location = buildRedirectUri(redirectUri, code, state);

            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .location(location)
                    .build();
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "access_denied", "message", ex.getMessage()));
        }
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> tokenJson(@RequestBody TokenExchangeRequest request) {
        return exchangeToken(request.getGrantType(), request.getCode(), request.getRedirectUri(),
                request.getClientId(), request.getCodeVerifier());
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> tokenForm(
            @RequestParam(name = "grant_type") String grantType,
            @RequestParam(name = "code") String code,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "client_id") String clientId,
            @RequestParam(name = "code_verifier") String codeVerifier) {
        return exchangeToken(grantType, code, redirectUri, clientId, codeVerifier);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, @RequestBody(required = false) Map<String, String> body) {
        String token = ssoSessionService.extractToken(request);
        if (token != null) {
            ssoSessionService.revoke(token);
        }
        ResponseCookie expired = ssoSessionService.buildExpiredCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .body(Map.of("message", "Logged out"));
    }

    private ResponseEntity<?> exchangeToken(String grantType, String code, String redirectUri, String clientId, String codeVerifier) {
        if (!"authorization_code".equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }
        try {
            TokenResponse response = oAuthService.exchangeAuthorizationCode(code, clientId, redirectUri, codeVerifier);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant", "message", ex.getMessage()));
        }
    }

    private URI buildRedirectUri(String redirectUri, String code, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri).queryParam("code", code);
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }
        return builder.build().encode().toUri();
    }

    private URI buildLoginUri(String clientId, String redirectUri, String codeChallenge,
                               String codeChallengeMethod, String state, String error) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/oauth/login")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", codeChallengeMethod);
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }
        if (error != null && !error.isBlank()) {
            builder.queryParam("error", error);
        }
        return builder.build().encode().toUri();
    }

    private String renderLoginPage(String clientId, String redirectUri, String codeChallenge,
                                    String codeChallengeMethod, String state, String error) {
        String errorBlock = (error != null && !error.isBlank())
                ? "<p style=\"color:#b00020;margin:0 0 12px;\">Invalid username or password.</p>"
                : "";
        return "<!DOCTYPE html>"
                + "<html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>Sign in - CSS</title>"
                + "<style>"
                + "body{font-family:-apple-system,Segoe UI,Roboto,sans-serif;max-width:360px;margin:80px auto;padding:0 16px;color:#222;}"
                + "h2{margin-bottom:24px;}"
                + "input{width:100%;padding:10px;margin:6px 0;box-sizing:border-box;border:1px solid #ccc;border-radius:4px;font-size:14px;}"
                + "button{width:100%;padding:10px;margin-top:12px;background:#1a73e8;color:#fff;border:none;border-radius:4px;font-size:15px;cursor:pointer;}"
                + "button:hover{background:#1558b0;}"
                + "</style></head><body>"
                + "<h2>Sign in</h2>"
                + errorBlock
                + "<form method=\"post\" action=\"/oauth/login\">"
                + "<input type=\"text\" name=\"username\" placeholder=\"Username\" required autofocus>"
                + "<input type=\"password\" name=\"password\" placeholder=\"Password\" required>"
                + "<input type=\"hidden\" name=\"client_id\" value=\"" + escapeHtml(clientId) + "\">"
                + "<input type=\"hidden\" name=\"redirect_uri\" value=\"" + escapeHtml(redirectUri) + "\">"
                + "<input type=\"hidden\" name=\"code_challenge\" value=\"" + escapeHtml(codeChallenge) + "\">"
                + "<input type=\"hidden\" name=\"code_challenge_method\" value=\"" + escapeHtml(codeChallengeMethod) + "\">"
                + "<input type=\"hidden\" name=\"state\" value=\"" + escapeHtml(state) + "\">"
                + "<button type=\"submit\">Sign in</button>"
                + "</form></body></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("'", "&#39;");
    }
}
