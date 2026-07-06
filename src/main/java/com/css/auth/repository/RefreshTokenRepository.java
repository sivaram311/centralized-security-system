package com.css.auth.repository;

import com.css.auth.model.RefreshToken;
import com.css.auth.model.RegisteredApplication;
import com.css.auth.model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    void deleteByUserAndApplication(UserAccount user, RegisteredApplication application);
}
