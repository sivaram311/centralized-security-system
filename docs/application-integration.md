# Application Integration Guide

How each application in the workspace connects to the Centralized Security System.

---

## Integration Overview

| Application | clientId | Status | Auth today |
|-------------|----------|--------|------------|
| [grok_dev](#1-grok_dev) | `grok-dev` | Documented, not wired | Embedded HS256 JWT |
| [persistent-agent-platform](#2-persistent-agent-platform) | `agent-platform` | ✅ Phase 5 wired | CSS JWT (replaces HTTP Basic) |
| [ERPNext](#3-erpnext) | `erpnext-bridge` | Planned | Frappe session/RBAC |

---

## 1. grok_dev

**Location:** `E:\Source\grok_dev`  
**Stack:** Angular 17 + Spring Boot 3.3 + PostgreSQL

### Integration Pattern: OAuth2 Resource Server

```
Angular ──login──► CSS (:9000)
Angular ──API───► grok_dev backend (:8081)  [validates CSS JWT via JWKS]
```

### Spring Boot Configuration (target)

```yaml
# application.yml (grok_dev backend)
css:
  issuer: http://localhost:9000
  jwks-uri: http://localhost:9000/.well-known/jwks.json
  client-id: grok-dev
```

### Validation Logic

Resource server must verify:
1. Signature (RS256 via JWKS)
2. `iss == css.issuer`
3. `aud` contains `grok-dev` OR `client_id == grok-dev`
4. `exp` not passed
5. Map `roles` claim → `GrantedAuthority`

### Role Mapping

| CSS role | grok_dev usage |
|----------|----------------|
| `ROLE_ADMIN` | Admin panel, privileged market config |
| `ROLE_USER` | Standard dashboard access |

### SSE Streams

Preserve query-token pattern on resource server for EventSource endpoints — validate CSS JWT from `?access_token=`.

**Full migration:** [migration-guide.md](./migration-guide.md)  
**Pattern reference:** [grok-dev-reference.md](./grok-dev-reference.md)

---

## 2. persistent-agent-platform

**Location:** `E:\MyWorkspace\persistent-agent-platform`  
**Stack:** Spring Boot + WebSocket + optional LangGraph sidecar

### Current Security (to replace)

```java
// SecurityConfig.java — HTTP Basic, single in-memory user
User.builder().username(securityProperties.getUsername())
    .password(encoder.encode(securityProperties.getPassword()))
    .roles("ADMIN")
```

Problems:
- Single shared credential in config
- No per-user audit trail
- WebSocket (`/ws/**`) currently permitAll

### Integration Pattern

```
Browser ──login──► CSS (:9000)
Browser ──REST──► agent-platform (:8080)  [JWT filter]
Browser ──WS────► agent-platform (/ws)    [JWT query param or STOMP header]
```

### Spring Boot Changes

1. Add JWT resource server filter (same as grok_dev)
2. Remove `InMemoryUserDetailsManager`
3. Protect `/ws/**` with token validation on handshake
4. Map `ROLE_ADMIN` → existing admin endpoints

### clientId Configuration

```yaml
css:
  client-id: agent-platform
  jwks-uri: http://localhost:9000/.well-known/jwks.json
```

### WebSocket Auth Options

| Method | Pros | Cons |
|--------|------|------|
| Query param `?token=` on connect | Simple | Token in URL logs |
| STOMP `Authorization` header | Clean | Requires STOMP client support |
| Session cookie after REST login | Standard | Needs cookie + CSRF handling |

**Recommendation:** Bearer token in WebSocket subprotocol or first STOMP frame — avoid query params in production.

---

## 3. ERPNext

**Location:** `E:\MyWorkspace\erpnext`  
**Stack:** Python/Frappe Framework v17

### Challenge

ERPNext authentication is deeply integrated with Frappe:
- Session cookies (`sid`)
- DocPerm / User Permission engine
- Website User vs System User model

CSS cannot simply replace Frappe login — requires a **bridge service**.

### Integration Pattern: erpnext-bridge

```
User ──login──► CSS (:9000)
erpnext-bridge ──validates CSS JWT──► maps user to Frappe User
erpnext-bridge ──creates Frappe session──► ERPNext desk
```

### Bridge Responsibilities

1. Validate CSS access token (JWKS)
2. Lookup or provision Frappe `User` by email/username
3. Assign Frappe roles based on CSS `roles` claim mapping:
   - `ROLE_SYSTEM_MANAGER` → Frappe System Manager
   - `ROLE_ACCOUNTS_USER` → Accounts User
4. Issue Frappe session cookie OR API key

### Alternative: Frappe OAuth/OIDC

If Frappe OAuth Provider app is installed, CSS can evolve to full OIDC:
- CSS as OIDC IdP
- ERPNext as OIDC Relying Party

See [erpnext-docs/security-architecture.md](../erpnext-docs/security-architecture.md) for Frappe security model context.

### clientId

`erpnext-bridge` — seeded with `ROLE_SYSTEM_MANAGER` for admin user in dev.

---

## 4. Generic Spring Boot Resource Server

Minimal integration steps for any new Java app:

### Step 1 — Register application

Add row to `registered_applications` or extend `DataSeeder.java`:
```java
seedApp(appRepo, "my-new-app", "My New Application");
```

### Step 2 — Assign user roles

Add `user_application_roles` entries linking users to app + roles.

### Step 3 — Add JWT filter

```java
@Component
public class CssJwtFilter extends OncePerRequestFilter {
    // Fetch JWKS from css.jwks-uri (cache 1 hour)
    // Parse Bearer token, verify RS256
    // Check aud/client_id matches css.client-id
    // Set SecurityContext from roles claim
}
```

### Step 4 — Configure CORS

Add app origin to CSS `css.cors.allowed-origin-patterns` in `application.yml`.

---

## 5. Angular / SPA Client Template

```typescript
@Injectable({ providedIn: 'root' })
export class CssAuthService {
  private base = environment.cssUrl;  // http://localhost:9000
  private clientId = environment.clientId;

  login(username: string, password: string) {
    return this.http.post<TokenResponse>(`${this.base}/auth/login`, {
      username, password, clientId: this.clientId
    }).pipe(tap(r => this.storeTokens(r)));
  }

  refresh() {
    return this.http.post<TokenResponse>(`${this.base}/auth/refresh`, {
      refreshToken: localStorage.getItem('refreshToken'),
      clientId: this.clientId
    }).pipe(tap(r => this.storeTokens(r)));
  }
}
```

Copy interceptor pattern from `grok_dev/frontend/src/app/interceptors/auth.interceptor.ts`.

---

## Environment Matrix

| Environment | CSS URL | Notes |
|-------------|---------|-------|
| Local dev | `http://localhost:9000` | H2, ephemeral keys |
| Staging | `https://auth.staging.example.com` | Postgres, persistent keys |
| Production | `https://auth.example.com` | HA deployment, TLS required |

All apps must update `css.issuer` and JWKS URL when promoting environments.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| 401 on login | No roles for clientId | Add user_application_roles |
| 403 on refresh | Wrong clientId in refresh body | Match login clientId |
| JWT invalid signature | CSS restarted with ephemeral keys | Re-login; use persistent keys in prod |
| aud mismatch | Token from wrong app | Login with correct clientId |
| CORS error | Origin not allowed | Add to css.cors.allowed-origin-patterns |

---

## Future: css-spring-boot-starter

Planned auto-configuration in `clients/spring-boot-starter/`:

```yaml
css:
  resource-server:
    enabled: true
    jwks-uri: http://localhost:9000/.well-known/jwks.json
    client-id: grok-dev
    issuer: http://localhost:9000
```

Single dependency + properties replaces manual filter wiring.
