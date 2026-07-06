package com.css.auth.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Loads or generates RS256 key pair for multi-application JWT signing.
 * Downstream apps validate tokens via JWKS without sharing a symmetric secret.
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    @Value("${css.keys.private-key-path:}")
    private Resource privateKeyResource;

    @Value("${css.keys.public-key-path:}")
    private Resource publicKeyResource;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId = "css-key-1";

    @PostConstruct
    void init() {
        try {
            if (privateKeyResource != null && privateKeyResource.exists()
                    && publicKeyResource != null && publicKeyResource.exists()) {
                privateKey = loadPrivateKey(privateKeyResource);
                publicKey = loadPublicKey(publicKeyResource);
                log.info("Loaded RSA key pair from classpath");
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                privateKey = (RSAPrivateKey) pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
                log.warn("Generated ephemeral RSA key pair — use persistent keys in production");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize JWT keys", ex);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    private RSAPrivateKey loadPrivateKey(Resource resource) throws Exception {
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
        }
    }

    private RSAPublicKey loadPublicKey(Resource resource) throws Exception {
        try (InputStream in = resource.getInputStream()) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(pem);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decoded));
        }
    }
}
