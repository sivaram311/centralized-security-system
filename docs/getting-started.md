# Getting Started

## Prerequisites

- Java 21+
- Maven 3.9+
- (Optional) PostgreSQL for production

## Run locally

```bash
git clone <repo-url>
cd centralized-security-system
mvn spring-boot:run
```

Server starts on **http://localhost:9000**.

## Verify installation

### 1. Health check

```bash
curl http://localhost:9000/actuator/health
```

### 2. Login

```bash
curl -s -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"admin123\",\"clientId\":\"grok-dev\"}"
```

Save `accessToken` and `refreshToken` from the response.

### 3. Current user

```bash
curl http://localhost:9000/auth/me \
  -H "Authorization: Bearer <accessToken>"
```

### 4. JWKS

```bash
curl http://localhost:9000/.well-known/jwks.json
```

### 5. Refresh

```bash
curl -s -X POST http://localhost:9000/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"<refreshToken>\",\"clientId\":\"grok-dev\"}"
```

## Registered applications (dev seed)

| clientId | Use for |
|----------|---------|
| `grok-dev` | Grok Dev trading platform |
| `agent-platform` | Persistent Agent Platform |
| `erpnext-bridge` | ERPNext SSO bridge (planned) |

## Configuration

Edit `src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 9000 | HTTP port |
| `css.issuer` | `http://localhost:9000` | JWT `iss` claim |
| `css.jwt.access-expiration-ms` | 900000 (15 min) | Access token TTL |
| `css.jwt.refresh-expiration-days` | 7 | Refresh token TTL |
| `css.cors.allowed-origin-patterns` | localhost origins | CORS for SPAs |

## Production checklist

- [ ] Switch datasource to PostgreSQL
- [ ] Set persistent RS256 keys (`css.keys.private-key-path` / `public-key-path`)
- [ ] Change default seed passwords
- [ ] Enable HTTPS; update `css.issuer`
- [ ] Disable H2 console
- [ ] Restrict CORS origins

See [security-model.md](./security-model.md).

## Next steps

- [Application Integration](./application-integration.md) — connect your app
- [Migration Guide](./migration-guide.md) — move from embedded auth
- [API Reference](./api-reference.md) — full endpoint list
