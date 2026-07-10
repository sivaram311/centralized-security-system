package com.css.resourceserver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "css.resource-server")
public class CssResourceServerProperties {

    private boolean enabled = false;
    private String jwksUri = "http://localhost:9000/.well-known/jwks.json";
    private String issuer = "http://localhost:9000";
    private String clientId = "my-app";
    private long jwksCacheSeconds = 3600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public long getJwksCacheSeconds() {
        return jwksCacheSeconds;
    }

    public void setJwksCacheSeconds(long jwksCacheSeconds) {
        this.jwksCacheSeconds = jwksCacheSeconds;
    }
}
