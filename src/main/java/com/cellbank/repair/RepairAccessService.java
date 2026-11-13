package com.cellbank.repair;

import java.util.Objects;

import com.cellbank.auth.User;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairAccessService {

    public boolean hasBroadRepairAccess(User user) {
        return hasRole(user, "ADMIN")
                || hasRole(user, "FRONT_DESK");
    }

    public boolean canAccessRepair(User user, RepairJob repair) {
        if (user == null || repair == null) {
            return false;
        }

        if (hasBroadRepairAccess(user)) {
            return true;
        }

        return hasRole(user, "TECHNICIAN")
                && user.getId() != null
                && Objects.equals(
                        user.getId(),
                        repair.getAssignedTechnicianId()
                );
    }

    public void requireRepairAccess(User user, RepairJob repair) {
        if (!canAccessRepair(user, repair)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have access to this repair."
            );
        }
    }

    private boolean hasRole(User user, String roleName) {
        return user != null
                && user.getRoles().stream()
                        .anyMatch(role ->
                                roleName.equals(role.getName()));
    }
}
