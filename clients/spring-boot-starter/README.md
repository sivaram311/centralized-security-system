# CSS Spring Boot Starter (Planned)

Auto-configuration library for downstream Spring Boot applications to validate CSS-issued JWTs.

## Target Usage

```xml
<dependency>
    <groupId>com.css</groupId>
    <artifactId>css-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```yaml
css:
  resource-server:
    enabled: true
    issuer: http://localhost:9000
    jwks-uri: http://localhost:9000/.well-known/jwks.json
    client-id: grok-dev
```

## Planned Features

- Auto-register `CssJwtAuthenticationFilter`
- JWKS cache with configurable TTL
- `aud` / `client_id` validation
- Role claim → `GrantedAuthority` mapping
- Optional SSE query-token support (grok_dev compatibility)

## Status

Not yet implemented — see [application-integration.md](../docs/application-integration.md) for manual integration steps.

Reference implementation to copy from:
- `grok_dev/backend/.../security/JwtAuthenticationFilter.java`
- `centralized-security-system/.../security/JwtAuthenticationFilter.java`
