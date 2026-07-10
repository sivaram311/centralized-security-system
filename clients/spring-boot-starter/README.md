# CSS Spring Boot Starter

Auto-configuration for Spring Boot 3 apps that validate CSS-issued RS256 JWTs via JWKS.

## Install

```powershell
cd E:\MyWorkspace\centralized-security-system\clients\spring-boot-starter
mvn -q install
```

```xml
<dependency>
  <groupId>com.css</groupId>
  <artifactId>css-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Configure

```yaml
css:
  resource-server:
    enabled: true
    issuer: http://localhost:9000
    jwks-uri: http://localhost:9000/.well-known/jwks.json
    client-id: agent-portal
    jwks-cache-seconds: 3600
    register-servlet-filter: false   # prefer SecurityFilterChain wiring
```

When `enabled=true`, beans `CssJwtValidator` and `CssJwtAuthenticationFilter` are created.

- Reads `Authorization: Bearer …`
- Also accepts `?access_token=` on `/ws/**` and SSE-style paths
- Verifies issuer, audience/`client_id`, and non-empty `roles`
- Sets `SecurityContext` authorities from the `roles` claim

Your app still owns `SecurityFilterChain`. Set `register-servlet-filter=false` and `addFilterBefore(cssJwtAuthenticationFilter, …)`.

## Reference consumers

- **Agent Portal** — depends on this starter (`css.resource-server.*`)
- Persistent Agent Platform / grok_dev can switch to the same dependency

## Build

```powershell
mvn -q -DskipTests package
```
