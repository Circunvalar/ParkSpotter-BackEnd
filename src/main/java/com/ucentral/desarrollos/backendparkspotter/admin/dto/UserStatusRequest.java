package com.ucentral.desarrollos.backendparkspotter.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(@NotNull Boolean enabled) {
}
