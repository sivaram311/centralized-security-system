# CSS APIs working — prove sheet (2026-07-15)

**Session:** `css-api-migrate-wave-2026-07-15`  
**Evidence:** `H:\releases\css-next-0.2.0\evidence\api-prove\SUMMARY.md`  
**Purpose:** Documented proof that IdP surfaces work **before** consumer-app migration coding.

## Fleet map

| App id | Public host | Local port | Role |
|--------|-------------|------------|------|
| `css` (classic) | https://css.delena.buzz | `:5900` | Live issuer for existing password/JWT consumers · tag `v0.1.0` |
| `css-next` | https://css-next.delena.buzz | `:5910` | Side fleet OAuth SSO · tag `v0.2.0` |
| CSS DEV | `http://127.0.0.1:9000` | `:9000` | Postgres DEV — **was down** during this prove |

Do **not** merge next into classic identity.

## Proved green

| Probe | Result |
|-------|--------|
| Classic JWKS `kid=css-key-1` RS256 | **200** (`:5900` + public host) |
| Classic `/actuator/health` | **UP** |
| css-next JWKS same kid | **200** (`:5910` + public) |
| css-next OIDC discovery | **200** · issuer `https://css-next.delena.buzz` |
| `GET /oauth/login` | **200** Sign-in page |
| `GET /oauth/authorize` (+ PKCE query) | **302** → `/oauth/login?...` (params preserved) |

## Expected non-green (documented)

| Probe | Result | Why |
|-------|--------|-----|
| `POST /auth/login` with README `admin`/`admin123` on `:5900`/`:5910` | **401** | PROD/PREPROD seeds use `CSS_ADMIN_PASSWORD` from env — not the README default |
| DEV `:9000` JWKS/login | **down** | Start DEV (Postgres profile) before consumer DEV wiring against localhost |

## GO for consumer wiring

**Yes — GO** to plan and implement app integrations that:

1. Validate JWTs via classic or css-next **JWKS** (`/.well-known/jwks.json`).
2. Use css-next **OAuth authorize → login → token** for SSO pilots (start with ProdDeck DEV).
3. Keep `clientId` values from MyAgent [`CLIENT-REGISTRY.md`](E:/MyAgent/workflow/css/CLIENT-REGISTRY.md).

**Next:** consumer migrate wave doc + per-app DEV work (see MyAgent `workflow/css/` + migrate-wave checklist). Password login smoke on DEV requires bringing `:9000` up with Postgres — do that before labeling DEV password path “green.”
