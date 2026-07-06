# Executive Summary — Centralized Security System

**Project:** centralized-security-system (CSS)  
**Purpose:** Single authentication authority for multiple applications in the workspace  
**Reference:** Evolved from [grok_dev JWT security](https://github.com/) (`E:\Source\grok_dev\docs\security-jwt.md`)

---

## Problem

Applications currently authenticate independently:

| App | Auth today | Issue |
|-----|------------|-------|
| grok_dev | Embedded HS256 JWT + Postgres refresh tokens | Duplicated users; shared secret per deployment |
| persistent-agent-platform | HTTP Basic, in-memory user | No SSO, single shared credential |
| ERPNext | Frappe sessions | Separate identity store |

Users must log in separately; roles are not unified; secrets are duplicated.

---

## Solution

CSS is a **central Identity Provider** that:

1. Stores users and **per-application roles** in one database
2. Issues **RS256 JWT access tokens** (15 min) + **opaque refresh tokens** (7 days)
3. Publishes **JWKS** so apps validate tokens without sharing signing secrets
4. Scopes every login to a **`clientId`** (e.g. `grok-dev`, `agent-platform`)

---

## Architecture (One Line)

```
Apps login → CSS (:9000) → JWT with aud=clientId → Apps validate via JWKS locally
```

---

## Status

| Component | Status |
|-----------|--------|
| Auth server (Spring Boot) | ✅ v0.1 runnable |
| Multi-app login + refresh | ✅ |
| JWKS + introspect | ✅ |
| Documentation | ✅ |
| grok_dev integration | 📋 Documented, not wired |
| agent-platform integration | 📋 Documented |
| ERPNext bridge | 📋 Planned |
| Spring Boot starter client | 📋 Planned |

---

## Security Posture

**Strengths:** BCrypt passwords, stateless JWT, refresh revocation, RS256 asymmetric signing, app-scoped roles and refresh tokens.

**Gaps (v0.2):** Login rate limiting, MFA, audit log, refresh token rotation, production key management UI.

See [security-model.md](./security-model.md) for full threat analysis.

---

## Recommended Rollout

1. Deploy CSS on port 9000 (dev) / auth subdomain (prod)
2. Migrate **grok_dev** first (best-documented reference)
3. Replace **agent-platform** HTTP Basic
4. Build **erpnext-bridge** for Frappe handoff

Timeline: ~4 weeks for grok_dev + agent-platform per [migration-guide.md](./migration-guide.md).

---

## Quick Start

```bash
cd centralized-security-system
mvn spring-boot:run
curl -X POST http://localhost:9000/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123","clientId":"grok-dev"}'
```

**Default dev credentials:** `admin` / `admin123` — change before production.
