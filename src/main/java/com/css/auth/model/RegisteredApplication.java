package com.css.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "registered_applications", uniqueConstraints = {
        @UniqueConstraint(columnNames = "clientId")
})
@Getter
@Setter
public class RegisteredApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String clientId;

    @Column(nullable = false, length = 128)
    private String displayName;

    /** Comma-separated redirect URIs for OAuth-style flows (future) */
    @Column(length = 1024)
    private String redirectUris;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Optional client secret for confidential clients (hashed) */
    @Column(length = 255)
    private String clientSecretHash;
}
