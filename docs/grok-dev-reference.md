# grok_dev Security Reference → CSS Mapping

This document maps the existing **grok_dev** authentication implementation (`E:\Source\grok_dev`) to the **Centralized Security System**, enabling a structured migration without losing proven patterns.

---

## Source Reference Files (grok_dev)

| File | Purpose |
|------|---------|
| `docs/security-jwt.md` | JWT strategy documentation |
| `backend/.../config/SecurityConfig.java` | Stateless security filter chain |
| `backend/.../security/JwtUtil.java` | HS256 token generation |
| `backend/.../security/JwtAuthenticationFilter.java` | Bearer token validation |
| `backend/.../controller/AuthController.java` | Login, refresh, logout, me |
| `backend/.../service/RefreshTokenService.java` | Opaque refresh token lifecycle |
| `backend/.../config/PasswordEncoderConfig.java` | BCrypt (breaks circular deps) |
| `frontend/.../services/auth.service.ts` | Token storage + session management |
| `frontend/.../interceptors/auth.interceptor.ts` | Bearer header + 401 refresh |

---

## Pattern Preservation

CSS intentionally preserves these grok_dev design decisions:

### 1. Stateless JWT Sessions

**grok_dev:**
```java
.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

**CSS:** Identical — no server-side HTTP sessions.

### 2. Access + Refresh Token Pair

**grok_dev flow** (from `security-jwt.md`):
1. Login → access JWT + opaque refresh UUID in DB
2. API calls use Bearer access token
3. 401 → interceptor calls `/refresh`
4. Logout → revoke refresh server-side

**CSS:** Same flow, with additions:
- Refresh tokens scoped to `clientId`
- Access tokens include `aud` claim

### 3. BCrypt Password Hashing

**grok_dev:** `PasswordEncoderConfig` with `BCryptPasswordEncoder`

**CSS:** Same dedicated config class pattern.

### 4. Refresh Token in Database

**grok_dev `RefreshTokenService`:**
```java
refreshToken.setToken(UUID.randomUUID().toString());
refreshToken.setExpiryDate(Instant.now().plusSeconds(7 * 24 * 60 * 60));
refreshTokenRepository.deleteByUser(user); // rotate on login
```

**CSS:** Same pattern, extended:
```java
refreshTokenRepository.deleteByUserAndApplication(user, application);
```

### 5. JwtAuthenticationFilter Structure

**grok_dev** skips filter for login/refresh/logout paths, parses Bearer header, loads UserDetails, validates token.

**CSS:** Same structure; validates RS256 locally without DB lookup on each request (roles embedded in JWT claims).

### 6. Angular Interceptor Compatibility

**grok_dev proactive refresh:** Refreshes if token expires in < 5 minutes.

**CSS:** Fully compatible — update:
- Login URL: `http://localhost:9000/auth/login`
- Refresh URL: `http://localhost:9000/auth/refresh`
- Add `clientId: 'grok-dev'` to both request bodies
- Store same `{accessToken, refreshToken}` shape

---

## Key Differences (Improvements)

### HS256 → RS256

**grok_dev `JwtUtil`:**
```java
@Value("${jwt.secret}")
private String secret;
// Keys.hmacShaKeyFor(keyBytes) — shared secret
```

**Problem:** Every grok_dev instance needs the same secret. Cannot safely share validation with other apps.

**CSS `JwtTokenService`:**
```java
.signWith(keyProvider.getPrivateKey(), Jwts.SIG.RS256)
```

Apps validate using `/.well-known/jwks.json` — **no secret sharing**.

### Global Roles → Per-Application Roles

**grok_dev `AuthController.getCurrentUser()`:**
```java
if ("admin".equals(auth.getName())) {
    response.put("roles", new String[]{"ROLE_ADMIN", "ROLE_USER"});
}
```

Roles are hardcoded/demo — stored implicitly in user record.

**CSS:** Explicit `user_application_roles` table:
```
admin + grok-dev     → ROLE_ADMIN, ROLE_USER
admin + agent-platform → ROLE_ADMIN
demo + grok-dev      → ROLE_USER only
```

### Access Token TTL

| | grok_dev | CSS |
|---|----------|-----|
| Access | 24 hours | 15 minutes |
| Refresh | 7 days | 7 days |

Shorter access TTL reduces exposure if token leaked; refresh handles UX.

### Login Request Contract

**grok_dev:**
```json
{ "username": "admin", "password": "admin123" }
```

**CSS:**
```json
{ "username": "admin", "password": "admin123", "clientId": "grok-dev" }
```

---

## SSE / EventSource Consideration

grok_dev supports query-token auth for SSE streams (EventSource cannot send Authorization header):

```java
// JwtAuthenticationFilter.java — grok_dev
if (isMarketSseStream(request)) {
    String queryToken = request.getParameter("access_token");
}
```

**CSS migration note:** Resource servers (grok_dev backend after migration) should preserve this pattern locally — validate CSS-issued JWT from query param on `/api/market/xauusd/*/stream` endpoints. CSS itself does not need this; it's a resource-server concern.

---

## Circular Dependency Pattern

grok_dev documents breaking the cycle:
```
JwtAuthenticationFilter → UserService → PasswordEncoder ← SecurityConfig
```

CSS applies the same fix:
- `PasswordEncoderConfig` — standalone bean
- `UserAccountService implements UserDetailsService` — injected into SecurityConfig

---

## Frontend Changes Required (grok_dev Angular)

### auth.service.ts

```typescript
private readonly CLIENT_ID = 'grok-dev';
private readonly CSS_BASE = 'http://localhost:9000';

login(username: string, password: string) {
  return this.http.post(`${this.CSS_BASE}/auth/login`, {
    username, password, clientId: this.CLIENT_ID
  });
}

refresh() {
  return this.http.post(`${this.CSS_BASE}/auth/refresh`, {
    refreshToken: this.getRefreshToken(),
    clientId: this.CLIENT_ID
  });
}
```

### auth.interceptor.ts

No structural change — still attach `Authorization: Bearer ${accessToken}`.

### Environment

Remove `jwt.secret` from grok_dev backend — validation uses JWKS URL instead.

---

## Backend Changes Required (grok_dev Spring Boot)

### Remove (after migration)
- `JwtUtil.java` (signing moves to CSS)
- `RefreshTokenService.java` + entity
- `AuthController` login/refresh endpoints (proxy or remove)
- `jwt.secret` from application.properties

### Add
- OAuth2 Resource Server dependency OR custom JWKS validator
- `css.jwks-uri=http://localhost:9000/.well-known/jwks.json`
- `css.client-id=grok-dev`
- JwtAuthenticationFilter validates RS256 + `aud` claim

### Optional: Auth proxy mode

During transition, grok_dev can proxy login to CSS:
```java
@PostMapping("/api/auth/login")
public ResponseEntity<?> login(@RequestBody LoginRequest req) {
    req.setClientId("grok-dev");
    return restTemplate.postForEntity("http://localhost:9000/auth/login", req, Map.class);
}
```

---

## Test Parity Checklist

| grok_dev test | CSS equivalent |
|---------------|----------------|
| `AuthControllerTest` | Add `AuthControllerTest` with MockMvc |
| `JwtUtilTest` | `JwtTokenServiceTest` (RS256 sign/verify) |
| `RefreshTokenServiceTest` | `RefreshTokenServiceTest` (+ clientId scope) |
| `auth.interceptor.spec.ts` | Unchanged behavior, new URLs |

---

## Summary

CSS is a **direct evolution** of grok_dev's JWT architecture — not a replacement of patterns that work. The migration is primarily:

1. Move user/refresh storage to CSS
2. Switch HS256 → RS256 + JWKS
3. Add `clientId` to all auth calls
4. Shorten access token TTL
5. Scope roles per application

See [migration-guide.md](./migration-guide.md) for phased rollout steps.
