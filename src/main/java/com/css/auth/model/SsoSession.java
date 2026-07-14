package com.css.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sso_sessions", indexes = {
        @Index(columnList = "session_token", unique = true)
})
@Getter
@Setter
public class SsoSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Opaque value stored in the CSS SSO cookie */
    @Column(name = "session_token", nullable = false, unique = true, length = 128)
    private String sessionToken;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;
}
