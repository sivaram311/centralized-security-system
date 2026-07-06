package com.css.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "user_application_roles", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "application_id", "role_name"})
})
@Getter
@Setter
public class UserApplicationRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "application_id")
    private RegisteredApplication application;

    @Column(nullable = false, length = 64)
    private String roleName;
}
