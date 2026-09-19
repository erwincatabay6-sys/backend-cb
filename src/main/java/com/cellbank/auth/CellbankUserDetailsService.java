package com.cellbank.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CellbankUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CellbankUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login)
            throws UsernameNotFoundException {

        if (login == null || login.isBlank()) {
            throw new UsernameNotFoundException("Invalid login credentials.");
        }

        String identifier = login.strip();

        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmail(identifier))
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid login credentials."));

        String[] roleNames = user.getRoles().stream()
                .map(Role::getName)
                .toArray(String[]::new);

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(roleNames)
                .disabled(user.getStatus() != UserStatus.ACTIVE)
                .build();
    }
}
