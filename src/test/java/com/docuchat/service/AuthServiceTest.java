package com.docuchat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.docuchat.domain.UserAccount;
import com.docuchat.domain.UserAccountRepository;
import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import com.docuchat.web.dto.AuthResponse;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private static final SecretKey TEST_SIGNING_KEY = new SecretKeySpec(
            "a-test-signing-key-with-at-least-thirty-two-bytes".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");

    @Mock
    private UserAccountRepository users;

    private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(TEST_SIGNING_KEY));
    private final JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(TEST_SIGNING_KEY)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(users, passwordEncoder, jwtEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersNormalizedAccountWithHashedPasswordAndFifteenMinuteToken() {
        when(users.existsByEmail("person@example.com")).thenReturn(false);
        when(users.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register("Person@Example.com", "correct-horse-battery");

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).saveAndFlush(accountCaptor.capture());
        UserAccount saved = accountCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("person@example.com");
        assertThat(saved.getPasswordHash()).isNotEqualTo("correct-horse-battery");
        assertThat(passwordEncoder.matches("correct-horse-battery", saved.getPasswordHash())).isTrue();
        assertThat(response.userId()).isEqualTo(saved.getId());
        assertThat(response.expiresAt()).isEqualTo(NOW.plusSeconds(900));

        Jwt decoded = jwtDecoder.decode(response.accessToken());
        assertThat(decoded.getSubject()).isEqualTo(saved.getId().toString());
        assertThat(decoded.getExpiresAt()).isEqualTo(NOW.plusSeconds(900));
    }

    @Test
    void loginIssuesTokenForCorrectPassword() {
        UUID userId = UUID.randomUUID();
        UserAccount account = new UserAccount(
                userId,
                "person@example.com",
                passwordEncoder.encode("correct-horse-battery"),
                NOW);
        when(users.findByEmail("person@example.com")).thenReturn(Optional.of(account));

        AuthResponse response = authService.login("PERSON@example.com", "correct-horse-battery");

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("person@example.com");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void loginUsesSameErrorForUnknownAccountAndWrongPassword() {
        when(users.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("existing@example.com")).thenReturn(Optional.of(new UserAccount(
                UUID.randomUUID(),
                "existing@example.com",
                passwordEncoder.encode("correct-horse-battery"),
                NOW)));

        assertThatThrownBy(() -> authService.login("missing@example.com", "wrong-password"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ERR_INVALID_CREDENTIALS));
        assertThatThrownBy(() -> authService.login("existing@example.com", "wrong-password"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ERR_INVALID_CREDENTIALS));
    }

    @Test
    void registrationRejectsDuplicateEmail() {
        when(users.existsByEmail("person@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("person@example.com", "correct-horse-battery"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ERR_EMAIL_IN_USE));
    }

    @Test
    void registrationRejectsPasswordsThatExceedBcryptByteLimit() {
        assertThatThrownBy(() -> authService.register("person@example.com", "😀".repeat(19)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ERR_VALIDATION));
    }
}
