# Vision walkthrough — one login for all apps

**Project:** centralized-security-system  
**Date:** 2026-07-15  
**Audience:** Crew pre-work gate

## Problem today

CSS is the single user directory and IdP, but each app (Portal, ProdDeck, AgentVerse, …) still shows its own login form and posts `username + password + clientId` again. Same password every time — not true SSO.

## North star

1. User hits any app → if not signed in, redirect to **one CSS login** (no per-app password field).
2. After authenticating once, open other apps **without re-entering the password** while the SSO session is valid.
3. Each app still receives an **app-scoped token** (`aud` / `clientId`) — one session does not mean one mega-JWT for every app.
4. Roles and richer RBAC can deepen later; identity + SSO ship first.

## Phase 0 decision (accepted)

Browser SSO target: **OIDC Authorization Code + PKCE**. Details: [docs/adr/001-sso-mechanism.md](../../docs/adr/001-sso-mechanism.md).

## What we are building now (Phase 1)

Automated tests for login, refresh, logout, JWT claims, JWKS, and the Spring Boot starter validator — so SSO work in Phase 2 does not ship on an untested base.

## Out of scope (this sprint)

Per-app password databases, sharing CSS signing keys with apps, mandatory login for waived public-read apps, deploy/promote.

## References

- [docs/sso-and-test-roadmap.md](../../docs/sso-and-test-roadmap.md)
- [agent-portal CSS App Home vision](../../../agent-portal/docs/platform/CSS-APP-HOME.md)
