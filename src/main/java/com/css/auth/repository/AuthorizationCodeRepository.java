package com.css.auth.repository;

import com.css.auth.model.AuthorizationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorizationCodeRepository extends JpaRepository<AuthorizationCode, Long> {
    Optional<AuthorizationCode> findByCode(String code);
}
