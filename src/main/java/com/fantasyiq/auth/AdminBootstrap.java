package com.fantasyiq.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Creates the first admin from configuration, and only ever CREATES -- an
 * account that already has this email is left exactly as it is, never
 * promoted. Registration is open and emails aren't verified, so promoting an
 * existing account would let anyone who registers the admin's address first
 * take over the role.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AdminProperties adminProperties, UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.adminProperties = adminProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = adminProperties.email();
        String password = adminProperties.password();
        if (isBlank(email) || isBlank(password)) {
            return;
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != UserRole.ADMIN) {
                log.warn("ADMIN_EMAIL {} already has a non-admin account; not promoting it", email);
            }
            return;
        }

        try {
            userRepository.save(new User(email, passwordEncoder.encode(password), "Admin", UserRole.ADMIN));
            log.info("Created admin account for {}", email);
        } catch (DataIntegrityViolationException e) {
            // Another instance starting at the same moment created it first.
            log.info("Admin account for {} was created concurrently; nothing to do", email);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
