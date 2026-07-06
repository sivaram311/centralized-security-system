# Migration Guide — From Embedded Auth to CSS

Phased migration plan starting with **grok_dev** (best documented reference).

---

## Phase 0: Prerequisites

- [ ] CSS running on port 9000 (`mvn spring-boot:run`)
- [ ] Verify login: `curl -X POST localhost:9000/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123","clientId":"grok-dev"}'`
- [ ] Verify JWKS: `curl localhost:9000/.well-known/jwks.json`
- [ ] Export existing grok_dev users (if any custom users beyond admin)

---

## Phase 1: Parallel Run (grok_dev)

Run CSS alongside grok_dev embedded auth. No breaking changes.

### 1.1 Add CSS proxy endpoints (optional)

In grok_dev `AuthController`, add alternate endpoints that forward to CSS:

```java
@PostMapping("/api/auth/css-login")
public ResponseEntity<?> cssLogin(@RequestBody Map<String, String> body) {
    body.put("clientId", "grok-dev");
    return restTemplate.postForEntity(
        "http://localhost:9000/auth/login", body, Map.class).getBody();
}
```

### 1.2 Feature flag in Angular

```typescript
// environment.ts
export const environment = {
  useCss: true,
  cssUrl: 'http://localhost:9000',
  clientId: 'grok-dev'
};
```

Toggle between embedded and CSS login for testing.

### 1.3 Validate token on grok_dev backend

Add temporary endpoint:

```java
@GetMapping("/api/auth/validate-css-token")
public ResponseEntity<?> validate(@RequestHeader("Authorization") String auth) {
    // Fetch JWKS, validate RS256, return claims
}
```

---

## Phase 2: Resource Server Cutover (grok_dev backend)

### 2.1 Add JWKS validator

Create `CssJwtAuthenticationFilter` in grok_dev (or use spring-boot-starter when available):

- Replace `JwtUtil` validation with JWKS public key
- Check `aud` / `client_id == grok-dev`
- Extract roles from JWT claims (skip DB UserDetails lookup)

### 2.2 Remove embedded auth components

| Remove | Reason |
|--------|--------|
| `JwtUtil.java` | CSS signs tokens |
| `RefreshToken` entity + repo + service | CSS manages refresh |
| `AuthController` login/refresh/logout | CSS handles auth |
| `jwt.secret` property | No symmetric secret needed |
| User password auth in grok DB | Optional — keep user profile data only |

### 2.3 Update SecurityConfig

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**", "/api/welcome", "/error").permitAll()
    .anyRequest().authenticated()
)
.addFilterBefore(cssJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

Keep `/api/auth/me` locally if needed for preferences, or move preferences to CSS/user profile service.

---

## Phase 3: Frontend Cutover (grok_dev Angular)

### 3.1 Update AuthService

- Login → `POST http://localhost:9000/auth/login`
- Refresh → `POST http://localhost:9000/auth/refresh`
- Logout → `POST http://localhost:9000/auth/logout` with `{clientId}`

### 3.2 CORS

Ensure CSS `application.yml` includes grok_dev Angular origin:
```yaml
css.cors.allowed-origin-patterns: http://localhost:4200,...
```

### 3.3 Token storage

Keep existing localStorage pattern from grok_dev — same `{accessToken, refreshToken}` keys.

### 3.4 Proactive refresh

Preserve 5-minute proactive refresh from grok_dev — works unchanged with CSS tokens.

---

## Phase 4: User Migration

### 4.1 Export grok_dev users

```sql
SELECT username, password, email FROM users;
```

### 4.2 Import to CSS

BCrypt hashes are portable — insert directly into CSS `users.password_hash`.

For each user, insert `user_application_roles` for `grok-dev` client.

### 4.3 Decommission grok_dev users table auth

Keep `users` table only if needed for app-specific profile data (preferences, etc.) — link by username/email.

---

## Phase 5: persistent-agent-platform

Simpler migration — no existing JWT:

1. Add CSS JWT filter to `SecurityConfig`
2. Remove `InMemoryUserDetailsManager` and HTTP Basic
3. Set `security.enabled=true` with CSS validation
4. Update frontend (if any) to login via CSS
5. Secure WebSocket handshake

Estimated effort: 1–2 days.

---

## Phase 6: ERPNext Bridge

Most complex — separate project:

1. Create `erpnext-bridge` Spring Boot or Python service
2. OIDC or custom token exchange
3. Map CSS roles → Frappe roles
4. Session bootstrap for desk access

Estimated effort: 1–2 weeks (depends on Frappe OAuth setup).

---

## Rollback Plan

| Phase | Rollback |
|-------|----------|
| Phase 1 | Disable `useCss` feature flag |
| Phase 2 | Re-enable `JwtUtil` + embedded filter (keep code in branch) |
| Phase 3 | Point AuthService back to `/api/auth/login` |
| Phase 4 | Users still in grok DB if not deleted |

**Keep grok_dev embedded auth on a git branch until Phase 3 validated in staging.**

---

## Validation Checklist

- [ ] Login via CSS returns tokens with correct `aud`
- [ ] grok_dev API accepts CSS access token
- [ ] Refresh flow works after access expiry
- [ ] Logout revokes refresh (refresh returns 403)
- [ ] SSE stream works with query token
- [ ] ROLE_ADMIN-only UI elements still gated correctly
- [ ] demo user cannot access agent-platform (no roles)
- [ ] JWKS rotation handled (restart CSS → users re-login)

---

## Timeline Suggestion

| Week | Milestone |
|------|-----------|
| 1 | Phase 0–1: CSS deployed, parallel proxy tested |
| 2 | Phase 2: grok_dev backend resource server |
| 3 | Phase 3–4: Frontend + user migration |
| 4 | Phase 5: agent-platform |
| 5+ | Phase 6: ERPNext bridge planning |
