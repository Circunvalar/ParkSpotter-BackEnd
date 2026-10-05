package com.ucentral.desarrollos.backendparkspotter.garageManagement.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageUpdateRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GarageService {

    private final GarageRepository garageRepository;

    @Transactional
    public GarageResponse create(GarageRequest request, UserAccount owner) {
        validateSchedule(request.open24Hours(), request.openingTime(), request.closingTime());

        Garage garage = new Garage();
        garage.setOwner(owner);
        garage.setName(request.name());
        garage.setDescription(request.description());
        garage.setPhone(request.phone());
        garage.setAddressLine(request.addressLine());
        garage.setCity(request.city());
        garage.setState(request.state());
        garage.setCountry(request.country());
        garage.setPostalCode(request.postalCode());
        garage.setLatitude(request.latitude());
        garage.setLongitude(request.longitude());
        garage.setTotalSpots(request.totalSpots());
        garage.setAvailableSpots(request.totalSpots());
        garage.setPricePerHour(request.pricePerHour());
        garage.setOpen24Hours(request.open24Hours());
        garage.setOpeningTime(request.open24Hours() ? null : request.openingTime());
        garage.setClosingTime(request.open24Hours() ? null : request.closingTime());
        garage.setStatus(GarageStatus.ACTIVE);

        return toResponse(garageRepository.save(garage));
    }

    @Transactional(readOnly = true)
    public GarageResponse getById(UUID id) {
        return toResponse(findEntity(id));
    }

    @Transactional(readOnly = true)
    public List<GarageResponse> listActive() {
        return garageRepository.findByStatus(GarageStatus.ACTIVE).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GarageResponse> listByCity(String city) {
        return garageRepository.findByCityIgnoreCaseAndStatus(city, GarageStatus.ACTIVE).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GarageResponse> listMine(UserAccount owner) {
        return garageRepository.findByOwnerId(owner.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GarageResponse> findNearby(double latitude, double longitude, double radiusKm) {
        return garageRepository.findNearby(latitude, longitude, radiusKm).stream().map(this::toResponse).toList();
    }

    @Transactional
    public GarageResponse update(UUID id, GarageUpdateRequest request, UserAccount user) {
        Garage garage = findEntity(id);
        assertOwnership(garage, user);

        if (request.name() != null) garage.setName(request.name());
        if (request.description() != null) garage.setDescription(request.description());
        if (request.phone() != null) garage.setPhone(request.phone());
        if (request.addressLine() != null) garage.setAddressLine(request.addressLine());
        if (request.city() != null) garage.setCity(request.city());
        if (request.state() != null) garage.setState(request.state());
        if (request.country() != null) garage.setCountry(request.country());
        if (request.postalCode() != null) garage.setPostalCode(request.postalCode());
        if (request.latitude() != null) garage.setLatitude(request.latitude());
        if (request.longitude() != null) garage.setLongitude(request.longitude());
        if (request.pricePerHour() != null) garage.setPricePerHour(request.pricePerHour());

        if (request.totalSpots() != null) {
            if (garage.getAvailableSpots() > request.totalSpots()) {
                garage.setAvailableSpots(request.totalSpots());
            }
            garage.setTotalSpots(request.totalSpots());
        }
        if (request.availableSpots() != null) {
            if (request.availableSpots() > garage.getTotalSpots()) {
                throw new ApiException("availableSpots no puede ser mayor que totalSpots");
            }
            garage.setAvailableSpots(request.availableSpots());
        }

        boolean open24Hours = request.open24Hours() != null ? request.open24Hours() : garage.isOpen24Hours();
        LocalTimePair times = resolveSchedule(garage, request, open24Hours);
        validateSchedule(open24Hours, times.opening(), times.closing());
        garage.setOpen24Hours(open24Hours);
        garage.setOpeningTime(open24Hours ? null : times.opening());
        garage.setClosingTime(open24Hours ? null : times.closing());

        return toResponse(garageRepository.save(garage));
    }

    @Transactional
    public GarageResponse changeStatus(UUID id, GarageStatus status, UserAccount user) {
        Garage garage = findEntity(id);
        assertOwnership(garage, user);
        garage.setStatus(status);
        return toResponse(garageRepository.save(garage));
    }

    @Transactional
    public void delete(UUID id, UserAccount user) {
        Garage garage = findEntity(id);
        assertOwnership(garage, user);
        garageRepository.delete(garage);
    }

    private Garage findEntity(UUID id) {
        return garageRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
    }

    private void assertOwnership(Garage garage, UserAccount user) {
        boolean isOwner = garage.getOwner().getId().equals(user.getId());
        boolean isAdmin = user.getRoles().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getName()));
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("No tienes permisos sobre este garaje");
        }
    }

    private void validateSchedule(boolean open24Hours, LocalTime opening, LocalTime closing) {
        if (open24Hours) {
            return;
        }
        if (opening == null || closing == null) {
            throw new ApiException("openingTime y closingTime son obligatorios cuando el garaje no es 24 horas");
        }
    }

    private LocalTimePair resolveSchedule(Garage garage, GarageUpdateRequest request, boolean open24Hours) {
        if (open24Hours) {
            return new LocalTimePair(null, null);
        }
        var opening = request.openingTime() != null ? request.openingTime() : garage.getOpeningTime();
        var closing = request.closingTime() != null ? request.closingTime() : garage.getClosingTime();
        return new LocalTimePair(opening, closing);
    }

    private record LocalTimePair(LocalTime opening, LocalTime closing) {
    }

    private GarageResponse toResponse(Garage garage) {
        return new GarageResponse(
                garage.getId(),
                garage.getName(),
                garage.getDescription(),
                garage.getPhone(),
                garage.getOwner().getId(),
                garage.getOwner().getEmail(),
                garage.getAddressLine(),
                garage.getCity(),
                garage.getState(),
                garage.getCountry(),
                garage.getPostalCode(),
                garage.getLatitude(),
                garage.getLongitude(),
                garage.getTotalSpots(),
                garage.getAvailableSpots(),
                garage.getPricePerHour(),
                garage.isOpen24Hours(),
                garage.getOpeningTime(),
                garage.getClosingTime(),
                garage.getStatus(),
                garage.getCreatedAt(),
                garage.getUpdatedAt()
        );
    }
}
