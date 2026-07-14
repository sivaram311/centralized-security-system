# Crew Manifest — Centralized Security System

**Project:** centralized-security-system  
**Branch:** `feature/css-next` (from prod `v0.1.0`)  
**Status:** Active  
**Crew Lead:** Cursor CLI  
**Session:** `css-sso-phase1-2026-07-15`

## Members (this sprint)

| Role | Agent | Scope |
|------|-------|-------|
| Crew Lead / EM | parent Cursor | Plan, hire, validate, commit gate |
| Vision / Architect | Lead (inline) | Phase 0 SSO decision + action plan |
| QA — Auth API | subagent A | Login / refresh / logout / me / introspect tests |
| QA — JWT/JWKS | subagent B | Token claims + JWKS endpoint tests |
| QA — Starter | subagent C | `css-spring-boot-starter` validator unit tests |
| Docs Keeper | subagent D | Roadmap Phase 0/1 status + getting-started test notes |
| Security Auditor | Lead spot-check | No secrets in tests; seed passwords only test defaults |

## Pre-work gate

- [x] Vision: one CSS login for all apps; app-scoped tokens kept (`docs/sso-and-test-roadmap.md`)
- [x] Phase 0 SSO mechanism decided (`docs/adr/001-sso-mechanism.md`)
- [x] Phase 1 (tests only) approved — no SSO code, no F:/G: deploy
- [ ] Phase 2+ coding blocked until Phase 1 green + separate approval

## Hard rules

- Work only on `feature/css-next`
- No promote / no F: G: changes
- Update ACTIVITY-LOG + product docs (CONSCIOUS #12)
- Record CSS tag `v0.1.0` baseline in docs when noting deps
