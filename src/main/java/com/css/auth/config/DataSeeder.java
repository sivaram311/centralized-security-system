package com.css.auth.config;

import com.css.auth.model.RegisteredApplication;
import com.css.auth.model.UserAccount;
import com.css.auth.model.UserApplicationRole;
import com.css.auth.repository.RegisteredApplicationRepository;
import com.css.auth.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seed(UserAccountRepository userRepo,
                           RegisteredApplicationRepository appRepo,
                           PasswordEncoder passwordEncoder) {
        return args -> {
            RegisteredApplication grokDev = seedApp(appRepo, "grok-dev", "Grok Dev Trading Platform");
            RegisteredApplication agentPlatform = seedApp(appRepo, "agent-platform", "Persistent Agent Platform");
            RegisteredApplication erpnextBridge = seedApp(appRepo, "erpnext-bridge", "ERPNext SSO Bridge");
            RegisteredApplication agentPortal = seedApp(appRepo, "agent-portal", "Agent Portal");

            if (userRepo.findByUsername("admin").isEmpty()) {
                UserAccount admin = new UserAccount();
                admin.setUsername("admin");
                admin.setEmail("admin@css.local");
                admin.setPasswordHash(passwordEncoder.encode("admin123"));
                admin.setEnabled(true);

                admin.getApplicationRoles().add(role(admin, grokDev, "ROLE_ADMIN"));
                admin.getApplicationRoles().add(role(admin, grokDev, "ROLE_USER"));
                admin.getApplicationRoles().add(role(admin, agentPlatform, "ROLE_ADMIN"));
                admin.getApplicationRoles().add(role(admin, erpnextBridge, "ROLE_SYSTEM_MANAGER"));
                admin.getApplicationRoles().add(role(admin, agentPortal, "ROLE_ADMIN"));
                admin.getApplicationRoles().add(role(admin, agentPortal, "ROLE_USER"));

                userRepo.save(admin);
                log.info("Seeded admin user (admin / admin123) with roles across all applications");
            } else {
                ensureRole(userRepo, "admin", agentPortal, "ROLE_ADMIN");
                ensureRole(userRepo, "admin", agentPortal, "ROLE_USER");
            }

            if (userRepo.findByUsername("demo").isEmpty()) {
                UserAccount demo = new UserAccount();
                demo.setUsername("demo");
                demo.setEmail("demo@css.local");
                demo.setPasswordHash(passwordEncoder.encode("demo123"));
                demo.setEnabled(true);
                demo.getApplicationRoles().add(role(demo, grokDev, "ROLE_USER"));
                demo.getApplicationRoles().add(role(demo, agentPortal, "ROLE_USER"));
                userRepo.save(demo);
                log.info("Seeded demo user (demo / demo123) for grok-dev and agent-portal");
            } else {
                ensureRole(userRepo, "demo", agentPortal, "ROLE_USER");
            }
        };
    }

    private void ensureRole(UserAccountRepository userRepo, String username, RegisteredApplication app, String roleName) {
        userRepo.findByUsername(username).ifPresent(user -> {
            boolean exists = user.getApplicationRoles().stream()
                    .anyMatch(r -> r.getApplication() != null
                            && app.getClientId().equals(r.getApplication().getClientId())
                            && roleName.equals(r.getRoleName()));
            if (!exists) {
                user.getApplicationRoles().add(role(user, app, roleName));
                userRepo.save(user);
                log.info("Granted {} on {} to {}", roleName, app.getClientId(), username);
            }
        });
    }

    private RegisteredApplication seedApp(RegisteredApplicationRepository repo, String clientId, String name) {
        return repo.findByClientIdAndEnabledTrue(clientId).orElseGet(() -> {
            RegisteredApplication app = new RegisteredApplication();
            app.setClientId(clientId);
            app.setDisplayName(name);
            app.setEnabled(true);
            return repo.save(app);
        });
    }

    private UserApplicationRole role(UserAccount user, RegisteredApplication app, String roleName) {
        UserApplicationRole role = new UserApplicationRole();
        role.setUser(user);
        role.setApplication(app);
        role.setRoleName(roleName);
        return role;
    }
}
