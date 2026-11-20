package com.docuchat.service;

import com.docuchat.domain.UserAccount;
import com.docuchat.domain.UserAccountRepository;
import com.docuchat.exception.ApiException;
import com.docuchat.exception.ErrorCode;
import com.docuchat.web.dto.AuthResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(15);
    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;
    private static final String EMAIL_UNIQUE_CONSTRAINT = "uk_app_user_email";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password.";
    private static final String DUMMY_PASSWORD = "not-a-real-user-password";

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            UserAccountRepository users,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AuthResponse register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        validatePasswordLength(password);
        if (users.existsByEmail(normalizedEmail)) {
            throw emailInUse();
        }

        UserAccount user = new UserAccount(
                UUID.randomUUID(),
                normalizedEmail,
                passwordEncoder.encode(password),
                clock.instant());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            if (isEmailUniqueViolation(ex)) {
                throw emailInUse(ex);
            }
            throw ex;
        }
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        Optional<UserAccount> user = users.findByEmail(normalizeEmail(email));
        String passwordHash = user.map(UserAccount::getPasswordHash).orElse(dummyPasswordHash);
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            passwordEncoder.matches(DUMMY_PASSWORD, dummyPasswordHash);
            throw invalidCredentials();
        }
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);
        if (user.isEmpty() || !passwordMatches) {
            throw invalidCredentials();
        }
        return issueToken(user.get());
    }

    private AuthResponse issueToken(UserAccount user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ACCESS_TOKEN_LIFETIME);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        Jwt token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims));
        return new AuthResponse(token.getTokenValue(), "Bearer", expiresAt, user.getId(), user.getEmail());
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            throw new ApiException(ErrorCode.ERR_VALIDATION,
                    "Password must not exceed 72 UTF-8 bytes.");
        }
    }

    private static boolean isEmailUniqueViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && EMAIL_UNIQUE_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private static ApiException emailInUse() {
        return new ApiException(ErrorCode.ERR_EMAIL_IN_USE, "An account with that email already exists.");
    }

    private static ApiException emailInUse(Throwable cause) {
        return new ApiException(ErrorCode.ERR_EMAIL_IN_USE,
                "An account with that email already exists.", cause);
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.ERR_INVALID_CREDENTIALS, INVALID_CREDENTIALS_MESSAGE);
    }
}
