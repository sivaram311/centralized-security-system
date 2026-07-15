# ProdDeck DEV ↔ css-next OAuth — Integration Spec

**Status:** **SUPERSEDED by live ProdDeck 0.8.4** (css-next hybrid on DEV/F/G). Kept as historical contract for PKCE/OAuth lanes.  
**Audience:** App implementers (ProdDeck), CSS maintainers, EM/QA  
**Date:** 2026-07-15 (pilot); live cutover same day → `v0.8.4`  
**Goal (original):** Pilot **ProdDeck DEV** (`:3320`) against **css-next** OAuth SSO without touching classic CSS or F/G ProdDeck bakes until the pilot is green.

**Live SoT:** ProdDeck pack `H:\releases\proddeck-0.8.4` · IdP css-next `v0.2.1` · mode `hybrid` · tracker `MIGRATE-PENDING.md` (wave complete).

Related:

| Doc | Role |
|-----|------|
| [ADR 001](./adr/001-sso-mechanism.md) | Why Authorization Code + PKCE |
| [api-reference.md](./api-reference.md#oauth-sso-phase-2--authorization-code--pkce) | Canonical CSS REST contracts |
| [sso-and-test-roadmap.md](./sso-and-test-roadmap.md) | Phase plan |
| `E:\MyAgent\workflow\css\integration.md` | Machine fleet table |
| `E:\MyAgent\workflow\deps\DEPENDENCY-MATRIX.md` | Version pins |

---

## 1. Decision summary (what to build)

| Choice | Value | Why |
|--------|-------|-----|
| Pilot app | ProdDeck DEV worktree `E:\wt\proddeck-integrate` (port **3320**) | Live integrate tree; already `clientId=proddeck` |
| IdP fleet | **css-next** only | Classic `css` / `css.delena.buzz` stays password-era for existing apps |
| Auth UX | Redirect gate (“Sign in with CSS”) — **no** username/password on ProdDeck when OAuth mode is on | Password lives on CSS `/oauth/login` |
| Protocol | OIDC-shaped **Authorization Code + PKCE (S256)** | ADR 001; already shipped on css-next `v0.2.0` |
| Keep legacy | Password path behind a flag (`password` default) | Safe rollback; F/G untouched |
| Promote F/G | **Out of scope** until DEV pilot evidence | Do not flip staging/prod ProdDeck issuer yet |

---

## 2. Topology (do not confuse fleets)

```text
┌──────────────────────────┐     authorize / token / JWKS      ┌─────────────────────────────┐
│ ProdDeck DEV :3320       │ ───────────────────────────────► │ css-next PROD :5910         │
│ E:\wt\proddeck-integrate │   https://css-next.delena.buzz   │ G:\apps\css-next            │
│ clientId = proddeck      │ ◄─── 302 + code ──────────────── │ tag v0.2.0 / OAuth + JWT    │
└──────────────────────────┘                                   └─────────────────────────────┘

Classic (LEAVE ALONE for this pilot):
  css.delena.buzz → G:\apps\css :5900 (v0.1.0) — still used by live F/G ProdDeck / AgentVerse / Portal
```

| Role | URL / port | Notes |
|------|------------|-------|
| ProdDeck DEV | `http://127.0.0.1:3320` | Browser origin for redirect_uri |
| css-next PROD (recommended pilot IdP) | `https://css-next.delena.buzz` → `:5910` | `proddeck` login verified; shared `app_css.prod` |
| css-next staging (optional) | `https://css-next-staging.delena.buzz` → `:4910` | Prefer only after credentials/seed confirmed |
| Classic CSS | `https://css.delena.buzz` | **Not** the pilot target |
| Local CSS DEV | `http://127.0.0.1:9000` | Use later for fully local loop; was down at last check |

**Issuer rule:** JWT `iss` must equal the public base you configure as `NEXT_PUBLIC_CSS_ISSUER` (no trailing slash mismatch). Server JWKS fetch uses `CSS_AUTH_URL` (same host in practice).

---

## 3. Current ProdDeck auth (as-is)

Password-only gate today:

```text
LoginForm
  → loginWithCss(AUTH_CONFIG, user, pass)
  → POST /api/css/auth/login   (Next.js BFF proxy)
  → CSS POST /auth/login { username, password, clientId: "proddeck" }
  → setTokens(access, refresh) in localStorage (prodDeck* keys)
  → verifySession / ensureFreshToken → POST /api/css/auth/refresh
```

Key files (read before coding):

| File | Responsibility |
|------|----------------|
| `src/lib/config.ts` | `AUTH_CONFIG` — `clientId: "proddeck"`, issuer from `NEXT_PUBLIC_CSS_ISSUER` |
| `src/lib/auth.ts` | Login, refresh, token storage, `isTokenAcceptable` (iss + aud/client_id) |
| `src/lib/jwt.ts` | Server Bearer verify via JWKS (`CSS_AUTH_URL` + issuer) |
| `src/components/LoginForm.tsx` | Collects username/password |
| `src/app/api/css/[...path]/route.ts` | Proxies to `CSS_AUTH_URL` with `redirect: "manual"` |
| `.env.example` | `CSS_AUTH_URL`, `NEXT_PUBLIC_CSS_ISSUER` |

**Gaps vs SSO:**

- No PKCE / authorize / callback
- Password UI on the app (not a login gate)
- Proxy unsuitable for browser **302** OAuth hops (see §6)

---

## 4. Target browser flow

```text
1. User opens http://127.0.0.1:3320/  (unauthenticated)
2. ProdDeck shows “Sign in with CSS” (oauth mode)
3. Browser generates:
     code_verifier  (43–128 URL-safe chars)
     code_challenge = BASE64URL(SHA256(code_verifier))
     state          (CSRF)
   Store verifier + state in sessionStorage (not localStorage)
4. Full-page navigate (NOT via /api/css):
     GET {ISSUER}/oauth/authorize
       ?response_type=code
       &client_id=proddeck
       &redirect_uri=http://127.0.0.1:3320/auth/callback
       &code_challenge=...
       &code_challenge_method=S256
       &state=...
5a. No CSS_SSO cookie → 302 → /oauth/login?... → user enters credentials on CSS HTML
5b. Valid CSS_SSO → 302 → redirect_uri?code=...&state=...  (no password)
6. ProdDeck /auth/callback:
     - Validate state vs sessionStorage
     - POST {ISSUER}/oauth/token  (JSON or form) with code + code_verifier
     - setTokens(accessToken, refreshToken, { username })
     - router.replace("/")
7. Subsequent API calls unchanged: Bearer access JWT; refresh via existing /auth/refresh path
```

Second app later: same authorize with SSO cookie → step 5b (true SSO).

---

## 5. API contracts (css-next)

Base URL examples: `https://css-next.delena.buzz` or `http://127.0.0.1:5910`.

### 5.1 `GET /oauth/authorize`

| Query | Required | Notes |
|-------|----------|-------|
| `response_type` | yes | Must be `code` |
| `client_id` | yes | Must be registered (`proddeck`) |
| `redirect_uri` | yes | Must pass allow-list (§5.5) |
| `code_challenge` | yes | S256 challenge |
| `code_challenge_method` | yes | `S256` (default) |
| `state` | recommended | Echoed on success redirect |

**Responses:**

| Condition | Status | Result |
|-----------|--------|--------|
| Bad `response_type` | 400 | `{ "error": "unsupported_response_type" }` |
| Unknown `client_id` | 400 | `{ "error": "invalid_client" }` |
| Bad `redirect_uri` | 400 | `{ "error": "invalid_redirect_uri" }` |
| No SSO session | 302 | `Location: /oauth/login?client_id&redirect_uri&code_challenge&...` |
| Valid SSO (`CSS_SSO`) | 302 | `Location: {redirect_uri}?code=...&state=...` |
| User has no roles for client | 400 | `{ "error": "access_denied", ... }` |

### 5.2 `GET /oauth/login` / `POST /oauth/login`

- **GET:** HTML login form (CSS-hosted). ProdDeck does not render this.
- **POST:** `application/x-www-form-urlencoded`  
  Fields: `username`, `password`, `client_id`, `redirect_uri`, `code_challenge`, `code_challenge_method`, `state`  
- Success: **302** to `redirect_uri?code=&state=` + `Set-Cookie: CSS_SSO=...` (HttpOnly)  
- Bad password: **302** back to `/oauth/login?...&error=invalid_credentials`

### 5.3 `POST /oauth/token`

Accepts **JSON** or **form-urlencoded**.

JSON body:

```json
{
  "grant_type": "authorization_code",
  "code": "<from redirect>",
  "redirect_uri": "http://127.0.0.1:3320/auth/callback",
  "client_id": "proddeck",
  "code_verifier": "<from sessionStorage>"
}
```

**Success 200** (`TokenResponse` — same shape as legacy login):

```json
{
  "accessToken": "<JWT>",
  "refreshToken": "<opaque>",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "username": "admin",
  "clientId": "proddeck",
  "roles": ["ROLE_ADMIN", "..."]
}
```

**Errors 400:** `unsupported_grant_type` | `invalid_grant` (wrong/expired code, PKCE mismatch, redirect_uri mismatch).

**JWT expectations (access):**

| Claim | Expectation for ProdDeck |
|-------|--------------------------|
| `iss` | Exact issuer base (e.g. `https://css-next.delena.buzz`) |
| `aud` / `client_id` | `proddeck` |
| `alg` | RS256; verify via `GET /.well-known/jwks.json` |
| `sub` | Username |

### 5.4 Legacy (keep for refresh + flag fallback)

| Endpoint | Use in pilot |
|----------|----------------|
| `POST /auth/login` | Only if `authMode=password` |
| `POST /auth/refresh` | **Keep** after OAuth — refresh tokens still work |
| `GET /.well-known/jwks.json` | Server `jwt.ts` unchanged pattern |
| `POST /oauth/logout` | Optional later (revoke `CSS_SSO`) |

Refresh body (existing):

```json
{ "refreshToken": "...", "clientId": "proddeck" }
```

### 5.5 Redirect URI allow-list (css-next v0.2.0)

Implemented in `OAuthService.isRedirectUriAllowed`:

| Scheme | Host | Allowed? |
|--------|------|----------|
| `http` | `localhost` or `127.0.0.1` | Yes (any path/port) |
| `https` | `localhost`, `delena.buzz`, `*.delena.buzz` | Yes |
| other | — | No |

Pilot URI:

```text
http://127.0.0.1:3320/auth/callback
```

Also valid: `http://localhost:3320/auth/callback` — pick **one** and use it consistently in authorize + token exchange (exact string match on stored code).

### 5.6 Prerequisites on IdP

1. Application `proddeck` registered (already on css-next prod).
2. User has ≥1 role for `proddeck` (empty roles → `access_denied` at code issue).
3. CORS: browser `POST /oauth/token` from `http://127.0.0.1:3320` must be allowed (css-next CORS includes localhost/127.0.0.1). If blocked, exchange via BFF instead (§6).

---

## 6. Wiring rules (critical)

### Do

1. **Full browser navigation** to `{ISSUER}/oauth/authorize` (and let CSS own `/oauth/login`).
2. Call **`/oauth/token` against the issuer origin** (browser CORS or a dedicated BFF route that does *not* strip cookies needed for authorize — token exchange does not need SSO cookie).
3. Keep **`redirect_uri` identical** on authorize, login (hidden fields), and token.
4. Store PKCE verifier in **sessionStorage**; clear after exchange.
5. Reuse existing `setTokens` / `isTokenAcceptable` / `ensureFreshToken` after exchange.
6. Point **both** `CSS_AUTH_URL` and `NEXT_PUBLIC_CSS_ISSUER` at the same css-next base for the pilot.

### Do not

1. Send `/oauth/authorize` through `src/app/api/css/[...path]` — proxy uses `redirect: "manual"` and does not complete a browser login redirect chain cleanly.
2. Collect passwords in ProdDeck while `authMode=oauth`.
3. Change F/G ProdDeck `.env` / bake to css-next until DEV pilot + evidence.
4. Point classic consumers at css-next as a drive-by.
5. Share `prodDeck*` tokens with other apps; SSO is the **CSS cookie**, not a shared JWT.

### Proxy vs direct

| Call | Via `/api/css`? | Why |
|------|-----------------|-----|
| `/oauth/authorize` | **No** | Needs real browser 302 + cookies |
| `/oauth/login` | **No** | CSS HTML form |
| `/oauth/token` | Optional | Direct preferred if CORS OK; else `POST /api/css/oauth/token` |
| `/auth/refresh` | Yes (current) | Fine to keep |
| JWKS (server) | N/A | Server uses `CSS_AUTH_URL` directly |

---

## 7. Suggested ProdDeck implementation checklist

Implement behind a flag; default **password** so accidental deploys stay safe.

### 7.1 Config / env

```env
# DEV pilot (.env.local — local only; do not bake into F/G)
CSS_AUTH_URL=https://css-next.delena.buzz
NEXT_PUBLIC_CSS_ISSUER=https://css-next.delena.buzz
NEXT_PUBLIC_CSS_AUTH_MODE=oauth
NEXT_PUBLIC_CSS_OAUTH_REDIRECT_URI=http://127.0.0.1:3320/auth/callback
```

Suggested `AuthConfig` fields:

- `authMode?: "password" | "oauth"`
- `oauthRedirectUri?: string`

### 7.2 Code touch list

| Step | Change |
|------|--------|
| A | PKCE helpers + `beginCssOAuthLogin` + `completeCssOAuthCallback` in `auth.ts` |
| B | `LoginForm`: oauth → single CTA that calls begin; hide user/pass fields |
| C | New route `src/app/auth/callback/page.tsx` (client): read `code`/`state`/`error`, exchange, `router.replace("/")` |
| D | Wrap `useSearchParams` in Suspense if Next requires it |
| E | `.env.example` documents the four vars (no secrets) |
| F | Short note in ProdDeck OPS / README: DEV oauth pilot vs classic prod |

### 7.3 Leave unchanged initially

- `jwt.ts` verification logic (only env issuer/base change)
- localStorage key names
- Refresh path + DeckHome / helpdesk Bearer usage
- F/G `.env.production` baking classic `css.delena.buzz`

---

## 8. Acceptance / smoke (before calling pilot done)

Manual:

1. Cold browser → `:3320` → “Sign in with CSS” → lands on CSS login HTML on **css-next** host.
2. Valid user/password for `proddeck` → redirect to `/auth/callback` → home deck loads.
3. JWT in localStorage: `iss` = css-next issuer; `aud`/`client_id` = `proddeck`.
4. Hard refresh still authenticated (access or refresh works).
5. Logout clears ProdDeck tokens (SSO cookie may remain — document behavior).
6. Second authorize in same browser session (optional second app or repeat) skips password while `CSS_SSO` valid.
7. Wrong PKCE verifier / state → visible error, no partial token write.
8. Classic `https://css.delena.buzz` health still 200; F/G ProdDeck still on classic issuer.

Automated (follow CONSCIOUS #14 when shipping UI):

- Device Lab: Realme + desktop + tablet against DEV oauth path when practical.
- Contract tests already exist on CSS: `OAuthAuthorizeIT` (6 cases).

---

## 9. Credentials / env notes

- css-next **PROD** uses prod schema passwords (not DEV `admin123` unless that user was seeded that way).
- OAuth mode: user types password on **CSS**, so ProdDeck DEV default `admin123` is irrelevant in oauth mode.
- Never commit IdP passwords into ProdDeck docs or `.env.example`.

---

## 10. Dependency / promote follow-ups (after pilot green)

1. Record pilot pin: ProdDeck commit + CSS **css-next `v0.2.0`** in activity + optional DEV note in `DEPENDENCY-MATRIX.md` (DEV row / pilot footnote — do not rewrite live F/G pins until promote).
2. When promoting ProdDeck to use css-next: new release pack, `DEPENDENCIES.md`, Q1/Q2 evidence, EM GO; update matrix live pins.
3. Consumers still on classic remain on `css` `v0.1.0` until each migrates.

---

## 11. Out of scope (this pilot)

- Migrating Agent Portal / AgentVerse / live home ProdDeck to OAuth
- Per-app persisted redirect URI table (allow-list is host-based today)
- Full OIDC discovery document / `id_token`
- Global logout UX across all apps
- Changing classic CSS jar on `:4900` / `:5900`

---

## 12. Implementer quick reference

```text
ISSUER     = https://css-next.delena.buzz
CLIENT_ID  = proddeck
REDIRECT   = http://127.0.0.1:3320/auth/callback
AUTHORIZE  = GET  {ISSUER}/oauth/authorize?...
TOKEN      = POST {ISSUER}/oauth/token
REFRESH    = POST {ISSUER}/auth/refresh   (via existing /api/css proxy OK)
JWKS       = GET  {ISSUER}/.well-known/jwks.json
```

Contract source of truth in code: `OAuthController`, `OAuthService`, `TokenExchangeRequest`, `TokenResponse` on branch `feature/css-next` / tag `v0.2.0`.
