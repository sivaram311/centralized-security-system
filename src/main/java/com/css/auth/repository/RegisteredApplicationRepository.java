package com.css.auth.repository;

import com.css.auth.model.RegisteredApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegisteredApplicationRepository extends JpaRepository<RegisteredApplication, Long> {
    Optional<RegisteredApplication> findByClientIdAndEnabledTrue(String clientId);
}
