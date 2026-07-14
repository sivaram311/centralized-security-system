# Action plan — CSS Phase 0 + Phase 1 (parallel crew)

**Date:** 2026-07-15  
**Lead:** Cursor  
**Goal:** Finish Phase 0 (SSO decision) + Phase 1 (test foundation) fast with parallel specialists; validate; update docs; commit/push. **No deploy.**

## Why this slice first

SSO without tests is unsafe. Tests without an SSO mechanism decision wastes rework. So: **decide → test → then SSO code**.

## Phase 0 — SSO mechanism (Lead, serial, short)

| Decision | Choice |
|----------|--------|
| Browser SSO | **OIDC Authorization Code + PKCE** (target) |
| Why not shared cookie only | Multi-subdomain CSRF/cookie-domain risk; harder to reason about logout |
| Interim (Phase 2) | CSS-hosted login UI + redirect `returnUrl` + short-lived auth code → app exchanges for app-scoped tokens (OIDC-shaped, evolves to full OIDC) |
| App-scoped `aud` | **Keep** |

Artifact: `docs/adr/001-sso-mechanism.md`

## Phase 1 — Tests (parallel)

```text
                    ┌─────────────────────────┐
                    │  Crew Lead (validate)   │
                    └───────────┬─────────────┘
          ┌─────────────────────┼─────────────────────┐
          ▼                     ▼                     ▼
   Auth API IT            JWT / JWKS IT         Starter unit tests
   (subagent A)           (subagent B)          (subagent C)
          └─────────────────────┼─────────────────────┘
                                ▼
                         mvn test (Lead)
                                ▼
                         Docs Keeper (D)
                                ▼
                      commit + push feature/css-next
```

### Lane A — Auth API (`src/test/.../AuthApiIT.java`)

Use `@SpringBootTest` + `MockMvc` (or WebTestClient), default H2, seed on (`css.seed.enabled` default true).

| Case | Expect |
|------|--------|
| Login admin + `agent-portal` + `admin123` | 200, access+refresh, roles non-empty, clientId match |
| Bad password | 401 |
| Unknown clientId | 401 |
| User with no roles for client | 401 |
| Refresh with valid refresh+clientId | 200 new access |
| Refresh with garbage | 403 |
| Logout with Bearer + clientId | 200; refresh thereafter fails |
| GET JWKS or /auth/me as covered by B / optional here | — |

Defaults: `admin`/`admin123`, `demo`/`demo123`, clientIds from DataSeeder (`agent-portal`, `grok-dev`, …).  
Keys: ephemeral OK (`JwtKeyProvider` generates if PEM missing).

### Lane B — JWT/JWKS (`JwtClaimsAndJwksIT.java`)

| Case | Expect |
|------|--------|
| `GET /.well-known/jwks.json` | 200, keys array, kty RSA |
| Access token from login verifies with JWKS public key | signature OK |
| Claims: `iss`, `aud` contains clientId, `sub`, `roles`, `exp` | present/correct |
| Expired / tampered token rejected by parser | fail |

### Lane C — Starter (`clients/spring-boot-starter/.../CssJwtValidatorTest.java`)

Add junit to starter pom if missing. Pure unit tests with mocked JWKS or injected key:

| Case | Expect |
|------|--------|
| Valid token + matching clientId/issuer | Optional auth present |
| Wrong audience | empty |
| Expired | empty |
| Disabled properties | empty / skip |

### Lane D — Docs (after tests green, or draft in parallel then finalize)

- Mark Phase 0 done + Phase 1 progress in `sso-and-test-roadmap.md`
- `getting-started.md`: how to run `mvn test`
- `agents/crew-activity.md` session log
- Index ADR in `docs/README.md`

## Validation gate (Lead)

1. `mvn -q test` in main module  
2. `mvn -q test` in `clients/spring-boot-starter` (if tests added)  
3. No secrets committed  
4. Docs updated  
5. ACTIVITY-LOG row  
6. Commit + push `feature/css-next` only after green  

## Explicitly deferred (do not start in this sprint)

- Phase 2 SSO login UI / redirect code  
- Phase 3 App Home  
- Phase 4 RBAC expansion  
- Any F:/G:/H: promote  

## Success metrics

- [ ] ADR 001 merged on branch  
- [ ] ≥8 meaningful auth tests green  
- [ ] JWKS + claims covered  
- [ ] Starter validator covered  
- [ ] Docs reflect Phase 0 done / Phase 1 done  
- [ ] Pushed to origin/feature/css-next  
