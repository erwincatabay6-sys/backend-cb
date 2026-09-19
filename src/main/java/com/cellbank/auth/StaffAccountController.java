package com.cellbank.auth;


import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;

@RestController
@RequestMapping("/api/users")
public class StaffAccountController {

    private final StaffAccountService staffAccountService;

    public StaffAccountController(
            StaffAccountService staffAccountService) {

        this.staffAccountService = staffAccountService;
    }

    @GetMapping
    public List<StaffAccountResponse> getStaffAccounts() {

        return staffAccountService.getStaffAccounts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffAccountResponse createStaffAccount(
            @Valid @RequestBody CreateStaffAccountRequest request) {

        return staffAccountService.createStaffAccount(request);
        
    }
    @PutMapping("/{userId}")
    public StaffAccountResponse updateStaffAccount(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody UpdateStaffAccountRequest request) {

        return staffAccountService.updateStaffAccount(
                userId,
                request
        );
    }
    @PatchMapping("/{userId}/access")
    public StaffAccountResponse updateStaffAccess(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody UpdateStaffAccessRequest request) {

        return staffAccountService.updateStaffAccess(userId, request);
    }
}
