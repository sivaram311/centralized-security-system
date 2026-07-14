package com.css.auth.repository;

import com.css.auth.model.SsoSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SsoSessionRepository extends JpaRepository<SsoSession, Long> {
    Optional<SsoSession> findBySessionToken(String sessionToken);
}
