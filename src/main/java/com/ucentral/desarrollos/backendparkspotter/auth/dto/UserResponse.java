package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, Set<String> roles, boolean enabled) {
}
