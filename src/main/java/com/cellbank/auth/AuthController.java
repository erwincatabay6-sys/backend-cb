package com.cellbank.auth;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final CurrentUserService currentUserService;
	private final PasswordChangeService passwordChangeService;

	public AuthController(
	        CurrentUserService currentUserService,
	        PasswordChangeService passwordChangeService) {

	    this.currentUserService = currentUserService;
	    this.passwordChangeService = passwordChangeService;
	}

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {

        return new CsrfResponse(
                csrfToken.getHeaderName(),
                csrfToken.getToken()
        );
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {

        return currentUserService.getCurrentUser(
                authentication.getName()
        );
    }

    @PatchMapping("/me")
    public CurrentUserResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request) {

        return currentUserService.updateProfile(
                authentication.getName(),
                request
        );
    }
    
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {

        passwordChangeService.changePassword(
                authentication.getName(),
                request
        );
    }

    public record CsrfResponse(
            String headerName,
            String token
    ) {
    }
}
