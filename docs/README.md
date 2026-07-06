# Centralized Security System — Documentation Index

| Document | Audience | Description |
|----------|----------|-------------|
| [../README.md](../README.md) | All | Project overview and quick start |
| [executive-summary.md](./executive-summary.md) | Leadership, stakeholders | Business case and rollout status |
| [getting-started.md](./getting-started.md) | Developers | Install, verify, configure |
| [architecture.md](./architecture.md) | Architects, backend devs | System design, data model, flows |
| [api-reference.md](./api-reference.md) | Integrators | REST API contract |
| [grok-dev-reference.md](./grok-dev-reference.md) | grok_dev team | Mapping from embedded JWT → CSS |
| [application-integration.md](./application-integration.md) | All app teams | Per-app integration guide |
| [migration-guide.md](./migration-guide.md) | DevOps, leads | Phased rollout plan |
| [security-model.md](./security-model.md) | Security, compliance | Threat model and hardening |

## Related Workspace Documentation

| Project | Security docs |
|---------|---------------|
| ERPNext | [erpnext-docs/security-architecture.md](../../erpnext-docs/security-architecture.md) |
| grok_dev | `E:\Source\grok_dev\docs\security-jwt.md` |
| persistent-agent-platform | `SecurityConfig.java` (HTTP Basic — to migrate) |

## Quick Links

- **Run CSS:** `mvn spring-boot:run` (port 9000)
- **Login test:** See [api-reference.md](./api-reference.md#post-authlogin)
- **JWKS:** `GET /.well-known/jwks.json`
