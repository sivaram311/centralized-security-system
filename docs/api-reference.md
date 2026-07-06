# API Reference — Centralized Security System

**Base URL:** `http://localhost:9000`

---

## Authentication Endpoints

### POST `/auth/login`

Authenticate user for a specific registered application.

**Request:**
```json
{
  "username": "admin",
  "password": "admin123",
  "clientId": "grok-dev"
}
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "a1b2c3d4-e5f6-...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "admin",
  "clientId": "grok-dev",
  "roles": ["ROLE_ADMIN", "ROLE_USER"]
}
```

**Response 401:**
```json
{ "message": "Invalid credentials or unauthorized for client" }
```

**Notes:**
- `clientId` must match a registered application (`grok-dev`, `agent-platform`, `erpnext-bridge`)
- User must have at least one role for the requested application
- Replaces grok_dev `POST /api/auth/login` (adds `clientId`)

---

### POST `/auth/refresh`

Issue new access token using refresh token.

**Request:**
```json
{
  "refreshToken": "a1b2c3d4-e5f6-...",
  "clientId": "grok-dev"
}
```

**Response 200:**
```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "username": "admin",
  "clientId": "grok-dev",
  "roles": ["ROLE_ADMIN", "ROLE_USER"]
}
```

**Response 403:**
```json
{ "message": "Refresh token expired or revoked" }
```

Compatible with grok_dev Angular `AuthInterceptor` refresh flow — update URL and add `clientId` to body.

---

### POST `/auth/logout`

Revoke refresh token for current user and client.

**Headers:** `Authorization: Bearer <accessToken>`

**Request (optional body):**
```json
{ "clientId": "grok-dev" }
```

**Response 200:**
```json
{ "message": "Logged out successfully" }
```

---

### GET `/auth/me`

Return current authenticated user from access token.

**Headers:** `Authorization: Bearer <accessToken>`

**Query params:** `clientId` (optional) — include app-specific roles

**Response 200:**
```json
{
  "username": "admin",
  "roles": ["ROLE_ADMIN", "ROLE_USER"],
  "authenticated": true,
  "clientId": "grok-dev",
  "applicationRoles": ["ROLE_ADMIN", "ROLE_USER"]
}
```

---

### POST `/auth/introspect`

Validate a token and return claims (RFC 7662-inspired, simplified).

**Request:**
```json
{
  "token": "eyJhbGciOiJSUzI1NiIs...",
  "clientId": "grok-dev"
}
```

**Response 200 (active):**
```json
{
  "active": true,
  "sub": "admin",
  "aud": ["grok-dev"],
  "roles": ["ROLE_ADMIN", "ROLE_USER"],
  "client_id": "grok-dev",
  "exp": 1710000900
}
```

**Response 200 (inactive):**
```json
{ "active": false }
```

Use when resource server cannot validate locally or for debugging.

---

## Discovery Endpoints

### GET `/.well-known/jwks.json`

Returns JSON Web Key Set for RS256 signature verification.

**Response 200:**
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "css-key-1",
      "n": "...",
      "e": "AQAB"
    }
  ]
}
```

Resource servers cache this response and refresh on signature failure (`kid` mismatch).

---

### GET `/.well-known/openid-configuration`

Minimal OIDC discovery document (partial compliance).

**Response 200:**
```json
{
  "issuer": "http://localhost:9000",
  "jwks_uri": "http://localhost:9000/.well-known/jwks.json",
  "token_endpoint": "http://localhost:9000/auth/login",
  "grant_types_supported": ["password", "refresh_token"],
  "id_token_signing_alg_values_supported": ["RS256"]
}
```

---

## Health

### GET `/actuator/health`

Spring Boot actuator health check.

---

## Registered Client IDs

| clientId | Application |
|----------|-------------|
| `grok-dev` | E:\Source\grok_dev |
| `agent-platform` | E:\MyWorkspace\persistent-agent-platform |
| `erpnext-bridge` | ERPNext SSO integration (planned) |

---

## Error Conventions

| HTTP | Meaning |
|------|---------|
| 401 | Invalid credentials or missing/invalid Bearer token |
| 403 | Valid auth but refresh revoked / wrong client |
| 400 | Validation error (missing clientId, etc.) |

---

## grok_dev Endpoint Mapping

| grok_dev | CSS |
|----------|-----|
| `POST /api/auth/login` | `POST /auth/login` (+ `clientId`) |
| `POST /api/auth/refresh` | `POST /auth/refresh` (+ `clientId`) |
| `POST /api/auth/logout` | `POST /auth/logout` (+ `clientId` body) |
| `GET /api/auth/me` | `GET /auth/me` |
| N/A | `GET /.well-known/jwks.json` |
| N/A | `POST /auth/introspect` |

See [grok-dev-reference.md](./grok-dev-reference.md) for migration details.
