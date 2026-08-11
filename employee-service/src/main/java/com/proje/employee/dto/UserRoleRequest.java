package com.proje.employee.dto;

import com.proje.employee.entity.Role;
import jakarta.validation.constraints.NotNull;

public record UserRoleRequest(
        @NotNull(message = "Role is required")
        Role role
) {
}
