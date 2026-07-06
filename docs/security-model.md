# Security Model — Centralized Security System

Threat model, trust boundaries, and hardening guidance for CSS v0.1.

---

## Trust Boundaries

```
┌─────────────────────────────────────────────────────────────┐
│ UNTRUSTED: Browser / Mobile / External API callers          │
└───────────────────────────┬─────────────────────────────────┘
                            │ HTTPS (required in prod)
┌───────────────────────────▼─────────────────────────────────┐
│ SEMI-TRUSTED: Registered client applications                │
│  · Know clientId (public identifier)                        │
│  · Must not hold CSS private signing key                    │
│  · Validate tokens locally via JWKS (public key only)       │
└───────────────────────────┬─────────────────────────────────┘
                            │ Bearer JWT
┌───────────────────────────▼─────────────────────────────────┐
│ TRUSTED: Centralized Security System                        │
│  · Holds user credentials (BCrypt hashes)                   │
│  · Holds RSA private key                                    │
│  · Issues and revokes refresh tokens                        │
└───────────────────────────┬─────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────┐
│ TRUSTED: CSS database (Postgres in prod)                    │
└─────────────────────────────────────────────────────────────┘
```

---

## Threat Analysis

| Threat | Impact | Mitigation | Residual |
|--------|--------|------------|----------|
| Stolen access token | API access until expiry | 15m TTL, HTTPS only | Window of exposure |
| Stolen refresh token | Session renewal | Server-side storage + revocation; bind to clientId | Revoke on logout |
| ClientId spoofing on login | Wrong app roles in token | Roles filtered by clientId at login | N/A if CSS trusted |
| JWKS private key leak | Forge any token | HSM/secrets manager; key rotation | Catastrophic — rotate keys |
| Brute force login | Account compromise | Rate limiting (TODO), account lockout (TODO) | Add in v0.2 |
| CSRF on login | Session fixation | Stateless JWT — not cookie-based | Low for API clients |
| XSS in client app | Token theft from storage | Client app responsibility; httpOnly cookies (future) | Shared with grok_dev model |
| SQL injection | DB compromise | JPA parameterized queries | Standard Spring safety |
| Privilege escalation | Cross-app access | `aud` + role scoping per app | Test per clientId |
| Insider (System Manager) | Mass user/role changes | Audit log (TODO), MFA (TODO) | Operational control |

---

## Inherited from grok_dev (Proven Controls)

| Control | grok_dev | CSS |
|---------|----------|-----|
| BCrypt passwords | ✅ | ✅ |
| Stateless sessions | ✅ | ✅ |
| Refresh revocation | ✅ | ✅ (+ per client) |
| No token logging | ✅ (documented) | ✅ (no token in logs) |
| PasswordEncoder isolation | ✅ | ✅ |
| Rate limit on public forms | ✅ (contact us) | 🔲 TODO on login |

---

## CSS-Specific Controls

### RS256 Asymmetric Signing

- Private key only on CSS server
- Resource servers hold public key via JWKS
- Compromise of grok_dev backend does **not** yield signing capability

### Application Isolation

- Tokens include `aud: [clientId]`
- Refresh tokens bound to `user × application`
- Logout scoped to single application session

### Token Introspection

- `POST /auth/introspect` for centralized validation when local JWKS unavailable
- Returns `{active: false}` on any error (fail safe)

---

## Production Hardening Checklist

### Required

- [ ] Replace ephemeral RSA keys with persistent PEM/HSM keys
- [ ] PostgreSQL instead of H2
- [ ] HTTPS with valid certificate
- [ ] Set `css.issuer` to public HTTPS URL
- [ ] Remove default seed passwords (`admin123`)
- [ ] Restrict CORS to known origins (no wildcards in prod)
- [ ] Disable H2 console

### Recommended

- [ ] Login rate limiting (per IP + per username)
- [ ] Account lockout after N failed attempts
- [ ] Refresh token rotation (issue new refresh on each refresh)
- [ ] Audit log table (login, logout, failed auth, role changes)
- [ ] MFA/TOTP for admin users
- [ ] Secrets in vault (not application.yml)
- [ ] Network: CSS DB not publicly accessible

### Resource Server Checklist

- [ ] Cache JWKS with TTL + refresh on `kid` mismatch
- [ ] Validate `iss`, `aud`, `exp` on every request
- [ ] Do not accept HS256 tokens after migration
- [ ] Clock skew tolerance ≤ 30 seconds

---

## Comparison with ERPNext / Frappe Security

| Aspect | Frappe/ERPNext | CSS |
|--------|----------------|-----|
| Auth model | Session cookie + server-side session | Stateless JWT |
| Authorization | DocPerm, User Permission, hooks | Roles claim only (authn focus) |
| Portal isolation | Party-scoped queries | N/A — app-level concern |
| MFA | Via Frappe/System Settings | Planned |
| Audit | Access Log, Version | Planned audit table |

CSS complements — does not replace — ERPNext authorization. See [erpnext-docs](../erpnext-docs/).

---

## Incident Response

### Compromised User Password
1. Disable user in CSS `users.enabled = false`
2. Revoke all refresh tokens for user (all clients)
3. Force password reset

### Compromised Private Key
1. Generate new RSA key pair
2. Update JWKS (`kid` rotation)
3. All access tokens invalid immediately
4. Users re-authenticate via refresh (if refresh still valid) or login

### Compromised Refresh Token
1. User logout revokes client-scoped refresh
2. Admin API (future): revoke all sessions for user

---

## Security Contact

Report vulnerabilities following the same process as ERPNext/Frappe:
- [Frappe Security](https://frappe.io/security)
- Do not commit secrets, PEM private keys, or production credentials to this repository

---

## v0.2 Security Roadmap

1. Login rate limiter (`@RateLimiter` or bucket4j)
2. Refresh token rotation
3. Admin REST API for user/role management (protected)
4. Structured audit log
5. PKCE authorization code flow for SPAs (eliminate password grant in browser)
6. mTLS for confidential server-to-server clients
