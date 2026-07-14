# Getting Started

## Prerequisites

- Java 21+
- Maven 3.9+
- **PostgreSQL** on `127.0.0.1:5432` with database `app_css` and role `app_css_dev` (machine DDL already applied — see `E:\MyAgent\workflow\db\`)
- Secrets: `E:\MyAgent\workflow\db\secrets\postgres.env` (gitignored)

## Run DEV (Postgres — aligned with PREPROD/PROD)

```powershell
cd centralized-security-system
.\scripts\start-dev.ps1
```

This activates Spring profile **`dev`**: port **9000**, schema **`app_css.dev`**, user **`app_css_dev`**.

Manual equivalent:

```powershell
# after exporting CSS_DB_USER / CSS_DB_PASSWORD / CSS_JDBC_URL
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### Optional H2-only demo (not for machine DEV)

```bash
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
```

## Verify installation

### 1. Health check

```bash
curl http://localhost:9000/actuator/health
```

### 2. Password login (legacy API)

```bash
curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\",\"clientId\":\"grok-dev\"}"
```

### 3. SSO authorize (Phase 2)

Browser / redirect flow:

1. `GET /oauth/authorize?response_type=code&client_id=agent-portal&redirect_uri=http://127.0.0.1:8080/callback&code_challenge=<S256>&code_challenge_method=S256&state=xyz`
2. Login once at `/oauth/login`
3. Redirect back with `?code=...`
4. `POST /oauth/token` with `code` + `code_verifier` → app-scoped tokens

See [sso-and-test-roadmap.md](./sso-and-test-roadmap.md) and [adr/001-sso-mechanism.md](./adr/001-sso-mechanism.md).

### 4. JWKS

```bash
curl http://localhost:9000/.well-known/jwks.json
```

## Profiles

| Profile | DB | Port | Use |
|---------|----|------|-----|
| `dev` | Postgres `app_css.dev` | 9000 | Machine DEV (default path) |
| `preprod` | Postgres `app_css.preprod` | 4900 | F: |
| `prod` | Postgres `app_css.prod` | 5900 | G: |
| `test` | H2 mem | random | `mvn test` |
| `h2` | H2 mem | 9000 | Local demo without Postgres |

## Registered applications (seed)

| clientId | Use for |
|----------|---------|
| `grok-dev` | Grok Dev trading platform |
| `agent-platform` | Persistent Agent Platform |
| `erpnext-bridge` | ERPNext SSO bridge (planned) |
| `agent-portal` | Agent Portal (+ AgentVerse reuse) |

## Running tests

```bash
mvn test
```

Starter:

```bash
cd clients/spring-boot-starter
mvn test
```

Tests force profile **`test`** (H2). Suites: `AuthApiIT`, `JwtClaimsAndJwksIT`, `OAuthAuthorizeIT`, context load.

## Configuration

Shared defaults: `application.yml`. Env-specific datasource: `application-{dev,preprod,prod,test,h2}.yml`.

| Property | Description |
|----------|-------------|
| `server.port` | HTTP port (profile default) |
| `css.issuer` | JWT `iss` claim |
| `css.jwt.access-expiration-ms` | Access token TTL |
| `css.oauth.*` | SSO code TTL, SSO cookie name/secure/SameSite |
| `css.keys.*` | RS256 PEM paths |

## Production checklist

- [x] Postgres for DEV / PREPROD / PROD (schema-per-env)
- [ ] Persistent RS256 keys on each env drive
- [ ] Strong seed passwords via env (not defaults)
- [ ] HTTPS; `css.issuer` = public URL; `CSS_SSO_COOKIE_SECURE=true`
- [ ] CORS origins for each public app host
- [ ] Record CSS git tag on every consumer promote (`workflow/deps/`)
