# CSS consumer migrate wave — 2026-07-15

**Session:** `css-api-migrate-wave-2026-07-15`  
**Prove gate:** [css-api-prove-working.md](./css-api-prove-working.md) · `H:\releases\css-next-0.2.0\evidence\api-prove\`

## Prove context

| Surface | Verdict |
|---------|---------|
| Classic / css-next JWKS + css-next OAuth authorize→login | **Green** |
| CSS DEV `:9000` | **Down** at prove — hold localhost password labels until Postgres DEV up |
| Fleet split | classic ≠ css-next |

## 1. IN THIS WAVE (proposed — await EM GO)

| # | App | clientId | IdP target | Why |
|---|-----|----------|------------|-----|
| 1 | **ProdDeck** DEV `:3320` | `proddeck` | **css-next** | OAuth SSO pilot; contract [proddeck-css-next-oauth-pilot.md](./proddeck-css-next-oauth-pilot.md) |
| 2 | **Agent Portal** DEV | `agent-portal` | **css-next** | Next active consumer; password→classic today |
| 3 | **AgentVerse** DEV | `agent-portal` *(reuse)* | **css-next** | After Portal contract — shared clientId |

**Wave rule:** DEV `:3xxx` only. No F/G issuer flip. No classic↔next merge. Matrix after promote only.

**EM GO (2026-07-15):** proceed with **ProdDeck-only** css-next OAuth DEV pilot (inventory safer cut). Portal / AgentVerse deferred.

## 2. OUT OF WAVE

| Item | Reason |
|------|--------|
| h-drive-server, stack-pilot | waived-public-read |
| Library | no auth yet |
| agent-platform, grok-dev, erpnext-bridge | deferred / planned |
| agentverse-upgrade F/G | Dispatch SoT — separate fleet; not issuer flip this wave |
| F/G cutover / matrix | Phase 6 EM GO |

## 3. Risks

1. Shared `agent-portal` clientId (Portal + AV + upgrade)
2. DEV `:9000` down for password/JWKS-local smoke
3. PROD `admin`/`admin123` → 401 expected (env seed)
4. Accidental classic↔next mix on F/G

## 5. PENDING tracker (machine SoT)

Keep status in MyAgent: [`E:\MyAgent\workflow\css\MIGRATE-PENDING.md`](E:/MyAgent/workflow/css/MIGRATE-PENDING.md)

| ID | Pending |
|----|---------|
| `mig-portal` | Agent Portal DEV → css-next |
| `mig-av` | AgentVerse DEV → css-next (after Portal) |
| `mig-css-dev9000` | CSS DEV `:9000` Postgres |
| `mig-pd-merge-tag` | ProdDeck merge + tag/pack |
| `mig-phase6` | F/G cutover + matrix (EM GO) |
| `mig-idp-brand` | Optional branded css-next login page |
