package com.css.auth.service;

import com.css.auth.model.RegisteredApplication;
import com.css.auth.model.UserAccount;
import com.css.auth.model.UserApplicationRole;
import com.css.auth.repository.RegisteredApplicationRepository;
import com.css.auth.repository.UserAccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserAccountService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final RegisteredApplicationRepository applicationRepository;

    public UserAccountService(UserAccountRepository userAccountRepository,
                              RegisteredApplicationRepository applicationRepository) {
        this.userAccountRepository = userAccountRepository;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = account.getApplicationRoles().stream()
                .map(UserApplicationRole::getRoleName)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        return User.builder()
                .username(account.getUsername())
                .password(account.getPasswordHash())
                .disabled(!account.isEnabled())
                .authorities(authorities)
                .build();
    }

    public UserAccount requireUser(String username) {
        return userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    public RegisteredApplication requireApplication(String clientId) {
        return applicationRepository.findByClientIdAndEnabledTrue(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or disabled client: " + clientId));
    }

    public List<String> rolesForUserAndApp(UserAccount user, RegisteredApplication app) {
        return user.getApplicationRoles().stream()
                .filter(r -> r.getApplication().getClientId().equals(app.getClientId()))
                .map(UserApplicationRole::getRoleName)
                .distinct()
                .toList();
    }
}
