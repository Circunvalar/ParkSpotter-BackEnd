package com.ucentral.desarrollos.backendparkspotter.admin.service;

import com.ucentral.desarrollos.backendparkspotter.admin.dto.AdminUserResponse;
import com.ucentral.desarrollos.backendparkspotter.admin.dto.UserRolesRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.RoleNames;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RefreshTokenService;
import com.ucentral.desarrollos.backendparkspotter.auth.service.Roles;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/** Gestión de usuarios del panel de administración (Sprint 06, PB-36). */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;

    /**
     * @param text busca en correo y nombre (sin distinguir mayúsculas)
     * @param role DRIVER, OWNER o ADMIN (o null)
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String text, String role, Boolean enabled, int page, int size) {
        Specification<UserAccount> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (text != null && !text.isBlank()) {
                String pattern = "%" + text.strip().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("email")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("fullName"), "")), pattern)));
            }
            if (role != null && !role.isBlank()) {
                Subquery<UUID> withRole = query.subquery(UUID.class);
                var user = withRole.from(UserAccount.class);
                Join<UserAccount, Role> roles = user.join("roles");
                withRole.select(user.get("id")).where(
                        cb.equal(user.get("id"), root.get("id")),
                        cb.equal(roles.get("name"), roleName(role)));
                predicates.add(cb.exists(withRole));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.of(userRepository.findAll(spec, pageable), AdminUserService::toResponse);
    }

    @Transactional
    public AdminUserResponse setEnabled(UUID userId, boolean enabled, UserAccount admin) {
        UserAccount user = findUser(userId);
        if (user.getId().equals(admin.getId()) && !enabled) {
            throw new ApiException("No puedes deshabilitar tu propia cuenta");
        }
        user.setEnabled(enabled);
        if (!enabled) {
            refreshTokenService.revokeAllForUser(user);
        }
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserResponse setRoles(UUID userId, UserRolesRequest request, UserAccount admin) {
        UserAccount user = findUser(userId);
        Set<String> names = request.roles().stream().map(role -> roleName(role.name())).collect(Collectors.toSet());
        if (user.getId().equals(admin.getId()) && !names.contains(RoleNames.ADMIN)) {
            throw new ApiException("No puedes quitarte el rol de administrador a ti mismo");
        }
        names.add(RoleNames.USER);
        Set<Role> roles = new HashSet<>();
        names.forEach(name -> roles.add(Roles.findOrCreate(roleRepository, name)));
        user.setRoles(roles);
        return toResponse(userRepository.save(user));
    }

    private UserAccount findUser(UUID id) {
        return userRepository.findById(id)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));
    }

    static String roleName(String role) {
        String upper = role.strip().toUpperCase(Locale.ROOT);
        String name = upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
        if (!Set.of(RoleNames.DRIVER, RoleNames.OWNER, RoleNames.ADMIN, RoleNames.USER).contains(name)) {
            throw new ApiException("Rol desconocido: " + role);
        }
        return name;
    }

    private static AdminUserResponse toResponse(UserAccount user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new)),
                user.isEnabled(), user.getCreatedAt());
    }
}
