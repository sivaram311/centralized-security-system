# AI-DLC Inception Baseline - centralized-security-system

**Captured:** 2026-08-01 (as-is snapshot, not a target design)

## Purpose

Centralized Security System (CSS) is the workspace’s single sign-on and token authority: one identity store, one login flow, and application-scoped RS256 JWTs that downstream apps validate via JWKS. It exists so apps (grok_dev, persistent-agent-platform, Agent Portal, ERPNext bridge, and others) do not each maintain separate auth stores or shared symmetric secrets.

## Tech stack

| Layer | As stated in-repo |
|-------|-------------------|
| Language | Java **21** (`pom.xml` `<java.version>`) |
| Framework | Spring Boot **3.3.1** (parent), Spring Security, Spring Data JPA, Validation, Actuator |
| Build | Maven (`pom.xml`; artifact `com.css:centralized-security-system:0.1.0-SNAPSHOT`) |
| JWT | jjwt **0.12.5**, RS256 |
| DB | PostgreSQL (dev/preprod/prod profiles); H2 (test/h2/Docker default image) |
| Client library | `clients/spring-boot-starter` — `css-spring-boot-starter` **0.1.0-SNAPSHOT**, Spring Boot dependency management **3.3.5**, Java 21 |
| Container | Multi-stage `Dockerfile`: `maven:3.9-eclipse-temurin-21` build → `eclipse-temurin:21-jre-jammy`, exposes **9000** |
| Dev helper | `scripts/start-dev.ps1` (profile `dev`) |

No `package.json` / Gradle build for the main app.

## Current features (as-built)

### Auth API (`AuthController` — `/auth`)

- `POST /auth/login` — username/password + `clientId` → access JWT + opaque refresh
- `POST /auth/refresh` — new access token (client-scoped refresh)
- `POST /auth/logout` — revoke refresh for user × client (Bearer required)
- `GET /auth/me` — current user from access token (optional `clientId` for app roles)
- `POST /auth/introspect` — RFC 7662–inspired token introspection

### OAuth SSO Phase 2 (`OAuthController` — `/oauth`)

- `GET /oauth/authorize` — Authorization Code + PKCE; SSO cookie `CSS_SSO` or redirect to login
- `GET|POST /oauth/login` — HTML login form / credential submit
- `POST /oauth/token` — code exchange (JSON or form); app-scoped tokens
- `POST /oauth/logout` — revoke SSO session cookie
- Android custom-scheme OAuth redirect allow-list covered by tests (recent commits)

### Discovery / ops

- `GET /.well-known/jwks.json` — RS256 public JWKS
- `GET /.well-known/openid-configuration` — minimal OIDC discovery (partial; `token_endpoint` points at `/auth/login`)
- `GET /actuator/health` (and `info`) exposed

### Identity / seed (as coded)

- Users + per-application roles; BCrypt passwords; refresh tokens bound to user × client
- Seeded clients include: `grok-dev`, `agent-platform`, `erpnext-bridge`, `agent-portal`, `trading-portal`, `machine-sentinel`
- Default seed users `admin` / `demo` (passwords from `css.seed.*` / env)

### Consumer helper

- `css-spring-boot-starter` JWKS resource-server auto-configuration + `CssJwtValidatorTest`

### Tests present

- `AuthApiIT`, `JwtClaimsAndJwksIT`, `OAuthAuthorizeIT`, `CentralizedSecurityApplicationTests`
- Surefire configured to include `*IT.java` in the unit-test phase

### Docs present (shipped documentation, not product UI)

- Architecture, API reference, getting started, integration, migration, security model, SSO roadmap, ADRs, consumer migrate / API prove notes

## Deploy topology (known facts below - cross-check against what you find in-repo, note any discrepancy explicitly rather than silently picking one)

**Known facts (external SoT for this capture):**

- Shared infra port **9000** — DEV/shared CSS instance
- PREPROD **classic** CSS on `F:/apps/css` **:4900** (do not confuse with css-next **:4910**)
- PROD **classic** CSS on `G:/apps/css` **:5900**, `https://css.delena.buzz`
- Auth: this **is** the auth system — issues JWTs for other apps via JWKS

**In-repo cross-check:**

| Fact | Repo evidence | Match? |
|------|---------------|--------|
| DEV/shared **:9000** | `application.yml` / `application-dev.yml` default port 9000; README | **Aligned** |
| PREPROD **:4900** | `application-preprod.yml` `CSS_PORT` default **4900** | **Port aligned** |
| PROD **:5900** | `application-prod.yml` default **5900**; README | **Port aligned** |
| Classic paths `F:/apps/css`, `G:/apps/css` | Profile key defaults are `F:/apps/css-next/keys` and `G:/apps/css-next/keys` | **Discrepancy** — YAML defaults target **css-next** install paths, not classic `…/apps/css` |
| Classic public host `https://css.delena.buzz` | README production line cites `css.delena.buzz` + `G:\apps\css` :5900; prod CORS includes `https://css.delena.buzz`; prod **issuer default** is `https://css-next.delena.buzz` | **Discrepancy** — classic hostname vs css-next issuer default coexist in docs/config |
| css-next **:4910 / :5910** | Documented in `docs/proddeck-css-next-oauth-pilot.md` and `docs/css-api-prove-working.md` as side-fleet ports; **not** the profile YAML `server.port` defaults (those remain 4900/5900) | **Note** — docs describe a separate css-next listen map; this repo’s Spring profiles still default classic-style 4900/5900 while pointing issuers/keys at css-next |
| JWKS auth authority | Controllers + JWKS endpoint + starter validator | **Aligned** — this codebase is the IdP/token issuer |

## Known debt / gaps (as-is, factual)

Evidence from docs / checklists (no `TODO`/`FIXME` hits under `src/`):

- **Security TODOs** (`docs/security-model.md`): login rate limiting, account lockout, MFA, audit log, refresh-token rotation, vaulted secrets / key-management UI — listed as v0.2 / unchecked hardening items
- **Integration incomplete** (`docs/application-integration.md`, `docs/executive-summary.md`): grok_dev documented but not wired; ERPNext `erpnext-bridge` planned; App Home / shared CSS hub marked partial (`docs/sso-and-test-roadmap.md`)
- **Stale status in executive summary:** still marks Spring Boot starter as “planned” though `clients/spring-boot-starter` exists and is tested
- **OIDC discovery partial:** `openid-configuration` does not advertise `/oauth/*` as the authorization-code surface; `token_endpoint` is `/auth/login`
- **api-reference registered clients table** omits newer seeded clients (`agent-portal`, `trading-portal`, `machine-sentinel`) present in `DataSeeder`
- **Classic vs css-next deploy naming** split across README, profile YAML, and pilot docs (see Deploy topology)

## Sources consulted

- `README.md`
- `docs/README.md`
- `docs/executive-summary.md`
- `docs/architecture.md`
- `docs/api-reference.md`
- `docs/application-integration.md`
- `docs/security-model.md`
- `docs/sso-and-test-roadmap.md`
- `docs/proddeck-css-next-oauth-pilot.md` (grep/snippet for ports 4910/5910)
- `docs/css-api-prove-working.md` (grep/snippet)
- `pom.xml`
- `clients/spring-boot-starter/pom.xml`
- `Dockerfile`
- `src/main/resources/application.yml`
- `src/main/resources/application-dev.yml`
- `src/main/resources/application-preprod.yml`
- `src/main/resources/application-prod.yml`
- `src/main/java/com/css/auth/controller/AuthController.java` (mapping scan)
- `src/main/java/com/css/auth/controller/OAuthController.java` (mapping scan)
- `src/main/java/com/css/auth/controller/WellKnownController.java`
- `src/main/java/com/css/auth/config/DataSeeder.java` (client seed list)
- `src/test/java/com/css/auth/*` (file list)
- `git status --short` / recent `git log` (context only)
