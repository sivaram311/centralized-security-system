# CSS — Test coverage, SSO checklist & roadmap

**Status:** Phase 0 **accepted**; Phase 1 **done** (Lead validated `mvn test` green — main + starter)  
**Branch:** `feature/css-next` (from prod tag `v0.1.0`)  
**Audience:** EM, security, app teams  
**Machine SoT for live pins:** `E:\MyAgent\workflow\deps\DEPENDENCY-MATRIX.md`

This note captures the current-state checklist, product intent (one login for all apps), and a phased roadmap. No deploy implied by this doc alone.

---

## 1. Checklist — current reality (`v0.1.0`)

| Item | Status | Notes |
|------|--------|-------|
| Unit / API tests for login | **Yes** | `AuthApiIT` on `feature/css-next` |
| Tests for refresh / logout / introspect | **Yes** | refresh + logout revoke in `AuthApiIT` |
| Tests for JWT claims (`aud`, roles, expiry) | **Yes** | `JwtClaimsAndJwksIT` |
| Tests for JWKS / `css-spring-boot-starter` | **Yes** | JWKS IT + `CssJwtValidatorTest` |
| One identity store (one password hash) | **Yes** | CSS holds users; apps must not invent IdPs |
| Same credentials across apps | **Yes** | Not a different password per app |
| True SSO (login once → open other apps without re-typing password) | **No** | Each app UI posts `username + password + clientId` again |
| App-scoped tokens | **Yes** | Login binds JWT/refresh to `clientId` / `aud` |
| Per-app login screens | **Yes (today)** | Portal, ProdDeck, AgentVerse each show their own form |
| Shared CSS login / App Home as hub | **Partial / planned** | See `agent-portal/docs/platform/CSS-APP-HOME.md` |
| Cross-app RBAC maturity | **Partial** | Roles exist per `clientId`; richer RBAC later is OK |
| Dependency / version tracking for CSS | **Yes** | CONSCIOUS #13 + `workflow/deps/` |

**Test verdict:** Phase 1 on `feature/css-next` adds real auth/JWKS/starter tests (Lead-validated). Prod tag `v0.1.0` predates these tests — consumers pin CSS release separately via `workflow/deps/`.

---

## 2. Product intent (agreed direction)

### What we already have

- **One password / one user directory** in CSS.
- Apps authenticate *through* CSS (`POST /auth/login` + JWKS validation).
- Tokens are **app-scoped** (`aud` / `clientId`) — good for isolation.

### What feels wrong today

- Each app’s login screen collects **username + password again**.
- That is a **UX/session gap**, not separate credentials.
- Pattern today:

```text
App UI  →  POST /auth/login { user, password, clientId }  →  app-scoped JWT
```

### North star

```text
Any app (or App Home)
  → if not signed in: ONE CSS login
  → then open other apps WITHOUT re-entering password
  → mint/exchange an app-scoped token for the clientId being opened
  → roles/RBAC tightened later
```

| Principle | Decision |
|-----------|----------|
| Password forms | Prefer **one CSS login**; per-app screens are **gates** that hand off to CSS, not second password collectors |
| App-scoped `aud` | **Keep** — one SSO session ≠ one mega-token for every app |
| Roles | Ship identity/SSO first; deepen RBAC in a later phase |
| Waived apps | Stack Pilot / H-Drive stay public-read unless design changes |

### Design caveats (decide before build)

| Topic | Options / note |
|-------|----------------|
| Browser SSO mechanism | **Decided:** OIDC Authorization Code + PKCE — see [ADR 001](./adr/001-sso-mechanism.md) |
| Token for each app | Silent exchange / “switch client” after SSO **or** redirect with auth code |
| Logout | Logout-one-app vs logout-everywhere |
| CSRF / cookie domain | Must be explicit if using cookies across subdomains |
| HTTPS only | Required for prod SSO |

---

## 3. Roadmap

Phases are sequential unless noted. Each phase ends with docs + tests green before the next promote.

### Phase 0 — Baseline & contracts (docs / no behavior change) ✅ accepted

| Deliverable | Done when | Status |
|-------------|-----------|--------|
| This roadmap published in CSS docs | Linked from `docs/README.md` | ✅ |
| Confirm prod tag `v0.1.0` is SoT for consumers | Matrix already pins CSS `v0.1.0` | ✅ |
| Decide SSO mechanism (cookie vs OIDC+PKCE) | Written decision in ADR | ✅ [ADR 001 — OIDC Auth Code + PKCE](./adr/001-sso-mechanism.md) |

**Exit:** Mechanism chosen; no prod cutover required. **Complete.**

---

### Phase 1 — Test foundation (build trust before SSO) ✅ done

Parallel crew on `feature/css-next` (2026-07-15): lanes A–D. Lead validated both modules green. See `agents/crew-activity.md`.

| Deliverable | Done when | Status |
|-------------|-----------|--------|
| Login success / bad password / unknown `clientId` | `AuthApiIT` | ✅ |
| Refresh success / revoked refresh | `AuthApiIT` | ✅ |
| Logout revokes refresh for `user × clientId` | `AuthApiIT` | ✅ |
| JWT claims: `iss`, `aud`, `exp`, roles for `clientId` | `JwtClaimsAndJwksIT` | ✅ |
| JWKS serves key used to verify issued tokens | `JwtClaimsAndJwksIT` | ✅ |
| Starter: reject bad/expired/wrong-aud token | `CssJwtValidatorTest` (5 cases) | ✅ |

**Exit met:** `mvn test` green (main: context + AuthApiIT + JwtClaimsAndJwksIT; starter: 5/5). Ready for Phase 2 design/coding after separate approval — still **no F:/G: deploy** from this slice.

---

### Phase 2 — One login UX (SSO session)

| Deliverable | Done when |
|-------------|-----------|
| Canonical CSS login page (or hosted login UI) | User enters password **once** |
| Apps redirect unauthenticated users to CSS login with `clientId` + `returnUrl` | No password field required on app (gate only) |
| After login: return to app with usable session/token for that `clientId` | App APIs accept Bearer JWT |
| Second app open: no password re-prompt while SSO session valid | Verified manually + automated smoke |
| Document integration for Portal / ProdDeck / AgentVerse | Update `workflow/css/integration.md` + app OPS |

**Exit:** Q1 evidence on staging hosts; dependency matrix still cites CSS tag under test.

---

### Phase 3 — App Home / launcher (optional but aligned)

| Deliverable | Done when |
|-------------|-----------|
| Post-login home lists apps user may open | Per `CSS-APP-HOME.md` vision |
| Click app → token for that `clientId` without password | Exchange or redirect works |
| CORS / subdomain checklist for each tile | Documented |

**Exit:** Home usable on staging; prod only after Q2.

---

### Phase 4 — Roles & access (RBAC maturity)

| Deliverable | Done when |
|-------------|-----------|
| Consistent role claims per `clientId` | Seed + admin path documented |
| Apps enforce roles on sensitive routes | At least one app proven end-to-end |
| Cross-app privilege escalation tests | Automated |
| Optional: MFA / lockout / rate limit (from security-model TODOs) | As prioritized |

**Exit:** RBAC is the default story; SSO remains the login story.

---

### Phase 5 — Harden & operate

| Deliverable | Done when |
|-------------|-----------|
| Rate limit / lockout on login | Per `security-model.md` TODOs |
| Audit log for admin role changes | Present |
| Key rotation runbook | Written + dry-run |
| Promote packs always list CSS `version` + `git tag` | CONSCIOUS #13 already required |

---

## 4. Suggested sequencing on `feature/css-next`

```text
Phase 0 (decision)
  → Phase 1 (tests)     ← do this before large SSO code
  → Phase 2 (SSO UX)
  → Phase 3 (App Home)  ← can overlap late Phase 2
  → Phase 4 (RBAC)
  → Phase 5 (harden)
```

Promote rule: each shippable slice gets its own semver / tag when ready for Q1/Q2; always record dependency pins.

---

## 5. Out of scope (for now)

- Replacing waived-public-read apps with mandatory CSS login (unless user directs)
- Per-app password databases
- Sharing CSS private signing keys with apps

---

## 6. Related docs

| Doc | Why |
|-----|-----|
| [adr/001-sso-mechanism.md](./adr/001-sso-mechanism.md) | Phase 0 decision — OIDC Auth Code + PKCE |
| [security-model.md](./security-model.md) | Threat model, TODOs (rate limit, MFA) |
| [application-integration.md](./application-integration.md) | How apps plug in today |
| [api-reference.md](./api-reference.md) | `/auth/login`, refresh, JWKS |
| `E:\MyAgent\workflow\css\integration.md` | Machine integration pattern |
| `E:\MyWorkspace\agent-portal\docs\platform\CSS-APP-HOME.md` | Launcher vision |
| `E:\MyAgent\workflow\deps\DEPENDENCY-MATRIX.md` | Live CSS version/tag pins |
