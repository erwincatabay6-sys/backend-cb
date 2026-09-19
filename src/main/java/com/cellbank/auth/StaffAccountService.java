package com.cellbank.auth;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffAccountService {

    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ADMIN",
            "TECHNICIAN",
            "FRONT_DESK"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionInvalidationService sessionInvalidationService;

    public StaffAccountService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            SessionInvalidationService sessionInvalidationService) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionInvalidationService = sessionInvalidationService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<StaffAccountResponse> getStaffAccounts() {

        return userRepository.findAll(Sort.by("id"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public StaffAccountResponse createStaffAccount(
            CreateStaffAccountRequest request) {

        String name = request.name().strip();
        String username = request.username().strip();
        String email = request.email().strip();
        String password = request.initialPassword();

        Set<Role> roles = resolveRolesForCreation(request.roles());

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Initial password must not exceed 72 UTF-8 bytes."
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username is already in use."
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already in use."
            );
        }

        User user = new User(
                name,
                username,
                email,
                passwordEncoder.encode(password)
        );

        user.setRoles(roles);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(false);

        User savedUser = userRepository.saveAndFlush(user);

        return toResponse(savedUser);
    }

    private Set<Role> resolveRolesForCreation(
            Set<String> requestedRoles) {

        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one role is required."
            );
        }

        for (String roleName : requestedRoles) {
            if (roleName == null || !ALLOWED_ROLES.contains(roleName)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "One or more roles are invalid."
                );
            }
        }

        if (requestedRoles.contains("ADMIN")) {

            if (requestedRoles.size() != 1) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "ADMIN cannot be combined with another role."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The administrator account is created during initial "
                            + "setup. Additional administrators are not allowed."
            );
        }

        Set<Role> roles = new HashSet<>();

        for (String roleName : requestedRoles) {

            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "A required staff role is not configured."
                    ));

            roles.add(role);
        }

        return roles;
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public StaffAccountResponse updateStaffAccount(
            Long userId,
            UpdateStaffAccountRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Staff account was not found."
                ));

        String name = request.name().strip();
        String username = request.username().strip();
        String email = request.email().strip();

        String previousUsername = user.getUsername();

        Set<String> previousRoles = new HashSet<>();

        for (Role role : user.getRoles()) {
            previousRoles.add(role.getName());
        }

        Set<Role> updatedRoles = resolveRolesForUpdate(
                previousRoles,
                request.roles()
        );

        boolean usernameTaken = userRepository.findByUsername(username)
                .filter(existingUser ->
                        !existingUser.getId().equals(userId))
                .isPresent();

        if (usernameTaken) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username is already in use."
            );
        }

        boolean emailTaken = userRepository.findByEmail(email)
                .filter(existingUser ->
                        !existingUser.getId().equals(userId))
                .isPresent();

        if (emailTaken) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already in use."
            );
        }

        boolean usernameChanged =
                !previousUsername.equals(username);

        boolean emailChanged =
                !user.getEmail().equalsIgnoreCase(email);

        boolean rolesChanged =
                !previousRoles.equals(request.roles());

        user.setFullName(name);
        user.setUsername(username);
        user.setEmail(email);
        user.setRoles(updatedRoles);

        if (emailChanged) {
            user.setEmailVerified(false);
        }

        User savedUser = userRepository.saveAndFlush(user);

        if (usernameChanged || emailChanged || rolesChanged) {
            sessionInvalidationService.expireSessionsAfterCommit(
                    previousUsername
            );
        }

        return toResponse(savedUser);
    }
    
    private Set<Role> resolveRolesForUpdate(
            Set<String> previousRoles,
            Set<String> requestedRoles) {

        if (requestedRoles == null || requestedRoles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one role is required."
            );
        }

        for (String roleName : requestedRoles) {
            if (roleName == null || !ALLOWED_ROLES.contains(roleName)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "One or more roles are invalid."
                );
            }
        }

        boolean currentlyAdmin = previousRoles.contains("ADMIN");
        boolean requestsAdmin = requestedRoles.contains("ADMIN");

        if (requestsAdmin && requestedRoles.size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN cannot be combined with another role."
            );
        }

        if (currentlyAdmin && !requestsAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The administrator account must retain the ADMIN role."
            );
        }

        if (!currentlyAdmin && requestsAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Additional administrators are not allowed."
            );
        }

        Set<Role> roles = new HashSet<>();

        for (String roleName : requestedRoles) {

            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "A required staff role is not configured."
                    ));

            roles.add(role);
        }

        return roles;
    }

    private StaffAccountResponse toResponse(User user) {

        List<String> roleNames = user.getRoles()
                .stream()
                .map(Role::getName)
                .sorted()
                .toList();

        return new StaffAccountResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                roleNames,
                user.getStatus()
        );
    }
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public StaffAccountResponse updateStaffAccess(
            Long userId,
            UpdateStaffAccessRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Staff account was not found."
                ));

        boolean active = request.active();

        boolean administrator = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        if (!active && administrator) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The administrator account cannot be deactivated."
            );
        }

        user.setStatus(
                active ? UserStatus.ACTIVE : UserStatus.INACTIVE
        );

        User savedUser = userRepository.saveAndFlush(user);

        if (!active) {
            sessionInvalidationService.expireSessionsAfterCommit(
                    savedUser.getUsername()
            );
        }

        return toResponse(savedUser);
    }
}


