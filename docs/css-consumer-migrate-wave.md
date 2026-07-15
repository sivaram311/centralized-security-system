# CSS consumer migrate wave — 2026-07-15

**Session:** `css-api-migrate-wave-2026-07-15`  
**Status:** **COMPLETE** (css-next consumer wave + classic-align leftovers)  
**Prove gate:** [css-api-prove-working.md](./css-api-prove-working.md) · packs `H:\releases\css-next-0.2.0\evidence\api-prove\` · brand `H:\releases\css-next-0.2.1\evidence\idp-brand\`  
**Machine SoT:** [`E:\MyAgent\workflow\css\MIGRATE-PENDING.md`](E:/MyAgent/workflow/css/MIGRATE-PENDING.md)

## Live pins (post-wave)

| App | Version | IdP | Mode | Notes |
|-----|---------|-----|------|-------|
| css-next | **0.2.1** / `v0.2.1` | — | OAuth + password | Minimal Delena `/oauth/login` brand |
| ProdDeck | **0.8.4** / `v0.8.4` | css-next | hybrid | home / home-staging / home-dev |
| Agent Portal | **0.1.9** | css-next | password | nginx `/auth` → `:5910` |
| AgentVerse-upgrade | **0.3.8** | css-next | password | shared `clientId=agent-portal` |
| Trading Portal | **0.1.0** + tip `cf5176d` | **classic** | JWKS | F `:4900` / G `:5900` by design |

## Wave history

1. Prove green (JWKS + OAuth authorize).
2. ProdDeck css-next pilot → briefly 0.8.2 F/G → rolled back to classic 0.8.3 → retry **0.8.4** hybrid.
3. Classic-align: Trading Portal F → classic `:4900`; CSS DEV `:9000` up.
4. css-next consumer wave: IdP brand 0.2.1 → Portal + AV-upgrade + ProdDeck on css-next; domain logins PASS.

## OUT OF WAVE (unchanged)

| Item | Reason |
|------|--------|
| h-drive-server, stack-pilot | waived-public-read |
| Library / grok-dev / erpnext-bridge | deferred |
| Classic densify `agentverse` | rollback-only; not Dispatch SoT |
| Portal Angular OAuth/PKCE | optional future (`mig-portal-oauth`) |

## Risks (remaining)

1. Shared `agent-portal` clientId (Portal + AV + upgrade) — intentional; move lockstep.
2. classic ≠ css-next JWKS/issuer — do not half-flip.
3. PROD password is env `CSS_ADMIN_PASSWORD` (not README `admin123`).

## CLOSED tracker IDs

`mig-tp-push` · `mig-idp-brand` · `mig-pd-css-next-retry` · `mig-portal` · `mig-av` · `mig-css-dev9000` (and earlier pd merge/phase6 work)

Optional later: `mig-portal-oauth`.
