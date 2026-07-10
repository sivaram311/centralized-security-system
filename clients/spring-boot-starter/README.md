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
```

When `enabled=true`, the starter registers `CssJwtAuthenticationFilter` which:

- Reads `Authorization: Bearer …`
- Also accepts `?access_token=` on `/ws/**` and SSE-style paths
- Verifies issuer, audience/`client_id`, and non-empty `roles`
- Sets `SecurityContext` authorities from the `roles` claim

Your app still owns `SecurityFilterChain` (`authorizeHttpRequests`). Add the filter before `UsernamePasswordAuthenticationFilter` if you prefer explicit ordering over the servlet registration bean.

## Reference consumers

- Agent Portal currently keeps an in-repo copy (`com.agentportal.security.CssJwtValidator`) and can migrate to this starter later.
- Persistent Agent Platform / grok_dev can switch to the same dependency.

## Build

```powershell
mvn -q -DskipTests package
```
