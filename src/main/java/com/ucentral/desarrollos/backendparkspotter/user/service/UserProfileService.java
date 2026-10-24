package com.ucentral.desarrollos.backendparkspotter.user.service;

import com.ucentral.desarrollos.backendparkspotter.auth.dto.UserResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RefreshTokenService;
import com.ucentral.desarrollos.backendparkspotter.auth.service.AuthService;
import com.ucentral.desarrollos.backendparkspotter.auth.service.Roles;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import com.ucentral.desarrollos.backendparkspotter.notification.repository.DeviceTokenRepository;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import com.ucentral.desarrollos.backendparkspotter.user.dto.AddRoleRequest;
import com.ucentral.desarrollos.backendparkspotter.user.dto.UpdateProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Perfil del usuario (Sprint 06, PB-05): editar datos, activar roles y eliminar la cuenta. */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    static final String DELETED_EMAIL_DOMAIN = "@deleted.parkspotter.invalid";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GarageRepository garageRepository;
    private final ReservationRepository reservationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final RefreshTokenService refreshTokenService;
    private final ParkingSpotService parkingSpotService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse me(UserAccount user) {
        return AuthService.toUserResponse(user);
    }

    @Transactional
    public UserResponse update(UpdateProfileRequest request, UserAccount user) {
        if (request.fullName() != null) user.setFullName(blankToNull(request.fullName()));
        if (request.phone() != null) user.setPhone(blankToNull(request.phone()));
        return AuthService.toUserResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse addRole(AddRoleRequest request, UserAccount user) {
        user.getRoles().add(Roles.findOrCreate(roleRepository, request.role().roleName()));
        return AuthService.toUserResponse(userRepository.save(user));
    }

    /**
     * Elimina la cuenta: no se borra la fila (las reservas y pagos se conservan para los reportes y
     * comprobantes del propietario), pero se anonimizan los datos personales (Ley 1581 de 2012), se
     * desactivan sus garajes y se cierran todas las sesiones. El correo queda libre para registrarse de nuevo.
     */
    @Transactional
    public void delete(String password, UserAccount user) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException("La contraseña no es correcta");
        }
        if (reservationRepository.countByDriverIdAndStatusIn(user.getId(), ReservationStatus.ACTIVE) > 0) {
            throw new ConflictException("Tienes reservas activas: cancélalas o espera a que terminen antes de eliminar la cuenta");
        }
        if (reservationRepository.countByGarageOwnerAndStatusIn(user.getId(), ReservationStatus.ACTIVE) > 0) {
            throw new ConflictException("Tus garajes tienen reservas activas: espera a que terminen antes de eliminar la cuenta");
        }
        garageRepository.findByOwnerId(user.getId()).forEach(garage -> {
            garage.setStatus(GarageStatus.INACTIVE);
            garageRepository.save(garage);
            parkingSpotService.publishAvailability(garage);
        });
        refreshTokenService.revokeAllForUser(user);
        deviceTokenRepository.deleteByUserId(user.getId());

        user.setEmail("deleted-" + UUID.randomUUID() + DELETED_EMAIL_DOMAIN);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setFullName(null);
        user.setPhone(null);
        user.setEnabled(false);
        user.setDeletedAt(Instant.now(clock));
        userRepository.save(user);
    }

    private static String blankToNull(String value) {
        String cleaned = value.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
