package com.fantasyiq.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrapFor(String email, String password) {
        return new AdminBootstrap(new AdminProperties(email, password), userRepository, passwordEncoder);
    }

    @Test
    void doesNothingWhenNoAdminIsConfigured() {
        bootstrapFor("", "").run(null);
        bootstrapFor(null, null).run(null);
        bootstrapFor("admin@fantasyiq.dev", "").run(null);

        verifyNoInteractions(userRepository);
    }

    @Test
    void createsTheAdminWhenNoSuchUserExists() {
        when(userRepository.findByEmail("admin@fantasyiq.dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-long-passphrase")).thenReturn("hashed");

        bootstrapFor("admin@fantasyiq.dev", "a-long-passphrase").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("admin@fantasyiq.dev");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed");
        assertThat(captor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void neverPromotesAnAccountThatAlreadyExists() {
        User existingRegularUser = new User("admin@fantasyiq.dev", "their-hash", "Someone");
        when(userRepository.findByEmail("admin@fantasyiq.dev")).thenReturn(Optional.of(existingRegularUser));

        bootstrapFor("admin@fantasyiq.dev", "a-long-passphrase").run(null);

        verify(userRepository, never()).save(any());
        assertThat(existingRegularUser.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void toleratesAnotherInstanceCreatingTheAdminFirst() {
        when(userRepository.findByEmail("admin@fantasyiq.dev")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("a-long-passphrase")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate email"));

        assertThatCode(() -> bootstrapFor("admin@fantasyiq.dev", "a-long-passphrase").run(null))
                .doesNotThrowAnyException();
    }
}
