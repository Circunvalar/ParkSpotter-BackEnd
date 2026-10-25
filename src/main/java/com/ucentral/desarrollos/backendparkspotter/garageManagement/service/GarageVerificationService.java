package com.ucentral.desarrollos.backendparkspotter.garageManagement.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.VerificationDecisionRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VerificationStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GaragePhotoRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;
import com.ucentral.desarrollos.backendparkspotter.notification.service.NotificationService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Flujo de verificación (Sprint 06, PB-26): el dueño sube fotos y pide la verificación; un administrador
 * la aprueba (insignia "verificado") o la rechaza con observaciones. Si el dueño cambia de opinión
 * puede volver a pedirla tras un rechazo.
 */
@Service
@RequiredArgsConstructor
public class GarageVerificationService {

    private final GarageRepository garageRepository;
    private final GaragePhotoRepository photoRepository;
    private final GarageService garageService;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public GarageResponse requestVerification(UUID garageId, UserAccount user) {
        Garage garage = findGarage(garageId);
        GarageAccessPolicy.assertCanManage(garage, user);
        if (garage.getVerificationStatus() == VerificationStatus.VERIFIED) {
            throw new ConflictException("El garaje ya está verificado");
        }
        if (garage.getVerificationStatus() == VerificationStatus.PENDING) {
            throw new ConflictException("La verificación ya está en revisión");
        }
        if (photoRepository.countByGarageId(garageId) == 0) {
            throw new ApiException("Sube al menos una foto del garaje antes de pedir la verificación");
        }
        garage.setVerificationStatus(VerificationStatus.PENDING);
        garage.setVerificationNotes(null);
        return garageService.toOwnerView(garageRepository.save(garage));
    }

    @Transactional(readOnly = true)
    public PageResponse<GarageResponse> listByStatus(VerificationStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("updatedAt"), Sort.Order.asc("id")));
        return PageResponse.of(garageRepository.findByVerificationStatus(status, pageable), garageService::toOwnerView);
    }

    @Transactional
    public GarageResponse decide(UUID garageId, VerificationDecisionRequest request) {
        Garage garage = findGarage(garageId);
        boolean approved = Boolean.TRUE.equals(request.approved());
        String notes = request.notes() == null || request.notes().isBlank() ? null : request.notes().strip();
        garage.setVerificationStatus(approved ? VerificationStatus.VERIFIED : VerificationStatus.REJECTED);
        garage.setVerificationNotes(notes);
        garage.setVerifiedAt(approved ? Instant.now(clock) : null);
        Garage saved = garageRepository.save(garage);

        if (approved) {
            notificationService.notify(saved.getOwner(), NotificationType.GARAGE_VERIFIED, "Tu garaje fue verificado",
                    "\"" + saved.getName() + "\" ya muestra la insignia de garaje verificado.", saved.getId(), true);
        } else {
            notificationService.notify(saved.getOwner(), NotificationType.GARAGE_REJECTED, "Verificación rechazada",
                    "La verificación de \"" + saved.getName() + "\" fue rechazada"
                            + (notes == null ? "." : ": " + notes), saved.getId(), true);
        }
        return garageService.toOwnerView(saved);
    }

    private Garage findGarage(UUID garageId) {
        return garageRepository.findById(garageId)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
    }
}
