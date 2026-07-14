package com.css.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "authorization_codes", indexes = {
        @Index(columnList = "code", unique = true)
})
@Getter
@Setter
public class AuthorizationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 64)
    private String clientId;

    @Column(nullable = false, length = 1024)
    private String redirectUri;

    @Column(nullable = false, length = 255)
    private String codeChallenge;

    @Column(nullable = false, length = 16)
    private String codeChallengeMethod;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean consumed = false;

    /** Opaque client state, echoed back on redirect (optional per OAuth spec) */
    @Column(length = 255)
    private String state;
}
