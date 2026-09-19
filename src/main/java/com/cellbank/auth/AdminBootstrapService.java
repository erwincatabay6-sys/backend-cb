package com.cellbank.auth;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminBootstrapService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            Validator validator) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public boolean createInitialAdmin(
            String fullName,
            String username,
            String email,
            String password) {

        if (userRepository.existsByRoles_Name("ADMIN")) {
            return false;
        }

        AdminDetails details = new AdminDetails(
                clean(fullName),
                clean(username),
                clean(email),
                password
        );

        Set<ConstraintViolation<AdminDetails>> errors =
                validator.validate(details);

        if (!errors.isEmpty()) {
            String messages = errors.stream()
                    .map(error -> error.getMessage())
                    .sorted()
                    .collect(Collectors.joining(" "));

            throw new IllegalStateException(
                    "Invalid administrator setup: " + messages
            );
        }

        if (details.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "Administrator password must not exceed 72 UTF-8 bytes."
            );
        }

        if (userRepository.existsByUsername(details.username())
                || userRepository.existsByEmail(details.username())) {

            throw new IllegalStateException(
                    "Administrator username is already in use."
            );
        }

        if (userRepository.existsByEmail(details.email())
                || userRepository.existsByUsername(details.email())) {

            throw new IllegalStateException(
                    "Administrator email is already in use."
            );
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "The ADMIN role is missing from the database."
                ));

        User admin = new User(
                details.fullName(),
                details.username(),
                details.email(),
                passwordEncoder.encode(details.password())
        );

        admin.setRoles(Set.of(adminRole));

        userRepository.save(admin);

        return true;
    }

    private String clean(String value) {
        return value == null ? null : value.strip();
    }

    private record AdminDetails(

            @NotBlank(message = "Full name is required.")
            @Size(max = 120, message = "Full name must not exceed 120 characters.")
            String fullName,

            @NotBlank(message = "Username is required.")
            @Pattern(
                    regexp = "[A-Za-z0-9._-]{3,50}",
                    message = "Username must contain 3 to 50 letters, digits, dots, underscores or hyphens."
            )
            String username,

            @NotBlank(message = "Email is required.")
            @Email(message = "Email format is invalid.")
            @Size(max = 254, message = "Email must not exceed 254 characters.")
            String email,

            @NotBlank(message = "Password is required.")
            @Size(
                    min = 15,
                    max = 72,
                    message = "Password must contain 15 to 72 characters."
            )
            String password

    ) {
    }
}
