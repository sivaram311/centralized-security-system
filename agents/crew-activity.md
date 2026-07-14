# Crew Activity — Centralized Security System

## 2026-07-15 — Phase 0 + 1 + 2 + DEV Postgres align

- **Session:** `css-sso-phase1-2026-07-15` / Phase 2 continue
- **Branch:** `feature/css-next` (from prod tag `v0.1.0`)
- **Lead:** Cursor
- **Result:** Phase 0–2 server work done; DEV→Postgres; `mvn test` 19 green; no F/G deploy

| Timestamp (IST) | Lane | Action | Result |
|-----------------|------|--------|--------|
| ~02:00 | Lead | Phase 0/1 plan + ADR + tests crew | ok → `cb5e14c` |
| ~02:26 | User | Proceed Phase 2 + align DEV Postgres | GO |
| ~02:30 | Lead | `application-dev.yml`, `application-test.yml`, `start-dev.ps1` | ok |
| ~02:35 | SSO agent | OAuth authorize/login/token + PKCE + `OAuthAuthorizeIT` | ok |
| ~02:39 | Lead | Validate `mvn test` (19) + docs (integration, getting-started, api-ref, registry) | ok |

### Test totals

| Class | Count |
|-------|-------|
| AuthApiIT | 6 |
| JwtClaimsAndJwksIT | 6 |
| OAuthAuthorizeIT | 6 |
| CentralizedSecurityApplicationTests | 1 |
| CssJwtValidatorTest (starter) | 5 |

### Deferred

- Wire Portal / ProdDeck / AgentVerse UIs to `/oauth/*` (consumer repos)
- Q1/Q2 promote of CSS beyond `v0.1.0`
- Restart live DEV :9000 onto Postgres (ops; secrets via start-dev.ps1)
