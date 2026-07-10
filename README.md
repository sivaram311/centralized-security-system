# Centralized Security System (CSS)

**Single sign-on and token authority for multiple applications in this workspace.**

| Property | Value |
|----------|-------|
| **Port** | 9000 |
| **Stack** | Spring Boot 3.3, Spring Security, JPA, RS256 JWT |
| **Reference implementation** | [grok_dev backend security](../../Source/grok_dev/docs/security-jwt.md) |
| **Status** | v0.1 — auth server + resource-server starter + integration docs |

---

## Why This Exists

Today each application manages its own authentication:

| Application | Current auth | Problem |
|-------------|--------------|---------|
| **grok_dev** | Embedded JWT (HS256) + refresh tokens in Postgres | Users/roles duplicated per app |
| **persistent-agent-platform** | HTTP Basic + in-memory user | No SSO, weak for multi-user |
| **ERPNext** | Frappe session/RBAC | Separate identity store |

**Centralized Security System (CSS)** provides one identity store, one login flow, and **application-scoped JWT tokens** that downstream services validate via **JWKS** — no shared symmetric secrets across apps.

---

## Quick Start

```bash
cd centralized-security-system
mvn spring-boot:run
```

**Default users (dev seed):**

| User | Password | Applications |
|------|----------|--------------|
| `admin` | `admin123` | grok-dev, agent-platform, agent-portal, erpnext-bridge |
| `demo` | `demo123` | grok-dev, agent-portal |

**Login example:**

```bash
curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","clientId":"grok-dev"}'
```

**JWKS (for token validation):**

```
GET http://localhost:9000/.well-known/jwks.json
```

---

## Registered Applications

| clientId | Application | Default roles |
|----------|-------------|---------------|
| `grok-dev` | Grok Dev (Angular + Spring Boot) | ROLE_USER, ROLE_ADMIN |
| `agent-platform` | Persistent Agent Platform | ROLE_ADMIN |
| `agent-portal` | Agent Portal (Cursor + Antigravity) | ROLE_USER, ROLE_ADMIN |
| `erpnext-bridge` | ERPNext SSO bridge (future) | ROLE_SYSTEM_MANAGER |

Each login request **must** include `clientId`. Tokens include `aud` (audience) and app-specific `roles`.

---

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture](./docs/architecture.md) | System design and token flows |
| [API Reference](./docs/api-reference.md) | REST endpoints |
| [grok_dev Reference](./docs/grok-dev-reference.md) | Mapping from grok_dev embedded auth → CSS |
| [Application Integration](./docs/application-integration.md) | How each app connects |
| [Migration Guide](./docs/migration-guide.md) | Step-by-step migration from embedded JWT |
| [Security Model](./docs/security-model.md) | Threat model and hardening |

---

## Project Structure

```
centralized-security-system/
├── src/main/java/com/css/auth/
│   ├── config/          SecurityConfig, DataSeeder, PasswordEncoder
│   ├── controller/      AuthController, WellKnownController (JWKS)
│   ├── model/           UserAccount, RegisteredApplication, RefreshToken
│   ├── security/        JwtKeyProvider, JwtTokenService, JwtAuthenticationFilter
│   └── service/         AuthenticationService, RefreshTokenService
├── docs/                Integration and architecture documentation
└── clients/spring-boot-starter/   css-spring-boot-starter (JWKS resource server)
```

---

## Relationship to grok_dev

CSS evolves the proven patterns from `E:\Source\grok_dev`:

- ✅ Stateless sessions (`SessionCreationPolicy.STATELESS`)
- ✅ Access + refresh token pair
- ✅ BCrypt passwords
- ✅ Refresh token revocation on logout
- ✅ Angular interceptor-compatible API shape
- 🆕 **RS256** instead of HS256 (apps validate via JWKS)
- 🆕 **Multi-app** `clientId` + per-app roles
- 🆕 **Token introspection** endpoint for opaque validation

See [grok-dev-reference.md](./docs/grok-dev-reference.md) for a field-by-field comparison.

---

## Next Steps

1. Integrate **grok_dev** backend as OAuth resource server (validate CSS JWT)
2. Replace **persistent-agent-platform** HTTP Basic with CSS JWT filter
3. Build **erpnext-bridge** (Frappe OAuth/OIDC or custom token exchange)
4. Publish `css-spring-boot-starter` from `clients/spring-boot-starter` (implemented; install locally with `mvn install`)
