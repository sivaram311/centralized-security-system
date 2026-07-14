# ADR 001 — Browser SSO mechanism for CSS

**Status:** Accepted  
**Date:** 2026-07-15  
**Branch:** `feature/css-next`  
**Deciders:** Crew Lead (user-directed SSO north star)

## Context

All apps use CSS as the single IdP, but today each app UI re-collects username/password with `clientId`. We need true “login once” across `*.delena.buzz` without collapsing into one mega-token for every app.

## Decision

**Target:** OIDC **Authorization Code + PKCE**.

**Phase 2 interim shape (compatible evolution):**

1. Unauthenticated app redirects to CSS login with `client_id`, `redirect_uri` / `returnUrl`, PKCE `code_challenge`.
2. User authenticates **once** on CSS.
3. CSS redirects back with a short-lived `code`.
4. App (or BFF) exchanges `code` + `code_verifier` for **app-scoped** access/refresh tokens (`aud` = that `clientId`).
5. Opening another app repeats exchange for that app’s `clientId` **without** password if CSS SSO session still valid.

## Alternatives considered

| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| Shared session cookie on `*.delena.buzz` only | Fast to ship | CSRF, cookie-domain pitfalls, awkward for non-browser clients | Reject as sole mechanism |
| Continue password-per-app UI | Already built | Not real SSO | Reject as end state |
| Full OIDC ASAP | Standard | Larger change set | Target; Phase 2 may ship OIDC-shaped subset first |

## Consequences

- Keep app-scoped `aud` / roles-per-clientId.
- Phase 1 tests lock current password+clientId API before SSO changes.
- Apps become **login gates** (redirect), not password collectors.
- Logout policy (one app vs everywhere) to be specified in Phase 2 design doc.
- Waived-public-read apps unchanged unless user directs otherwise.

## References

- `docs/sso-and-test-roadmap.md`
- `docs/security-model.md`
- `E:\MyWorkspace\agent-portal\docs\platform\CSS-APP-HOME.md`
