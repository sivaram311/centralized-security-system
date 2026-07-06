package com.css.auth.controller;

import com.css.auth.dto.IntrospectRequest;
import com.css.auth.dto.LoginRequest;
import com.css.auth.dto.RefreshTokenRequest;
import com.css.auth.security.JwtTokenService;
import com.css.auth.service.AuthenticationService;
import com.css.auth.service.RefreshTokenService;
import com.css.auth.service.UserAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final UserAccountService userAccountService;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthenticationService authenticationService,
                          RefreshTokenService refreshTokenService,
                          UserAccountService userAccountService,
                          JwtTokenService jwtTokenService) {
        this.authenticationService = authenticationService;
        this.refreshTokenService = refreshTokenService;
        this.userAccountService = userAccountService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            return ResponseEntity.ok(authenticationService.login(request));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials or unauthorized for client"));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            return ResponseEntity.ok(authenticationService.refresh(request));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) Map<String, String> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            String clientId = body != null ? body.get("clientId") : null;
            if (clientId != null) {
                refreshTokenService.revokeForUserAndClient(auth.getName(), clientId);
            }
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@RequestParam(required = false) String clientId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("username", auth.getName());
        response.put("roles", auth.getAuthorities().stream().map(Object::toString).toList());
        response.put("authenticated", true);

        if (clientId != null) {
            var user = userAccountService.requireUser(auth.getName());
            var app = userAccountService.requireApplication(clientId);
            response.put("clientId", clientId);
            response.put("applicationRoles", userAccountService.rolesForUserAndApp(user, app));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/introspect")
    public ResponseEntity<?> introspect(@Valid @RequestBody IntrospectRequest request) {
        try {
            var claims = jwtTokenService.parseAndValidate(request.getToken(), request.getClientId());
            return ResponseEntity.ok(Map.of(
                    "active", true,
                    "sub", claims.getSubject(),
                    "aud", claims.getAudience(),
                    "roles", claims.get("roles"),
                    "client_id", claims.get("client_id"),
                    "exp", claims.getExpiration().getTime() / 1000
            ));
        } catch (Exception ex) {
            return ResponseEntity.ok(Map.of("active", false));
        }
    }
}
