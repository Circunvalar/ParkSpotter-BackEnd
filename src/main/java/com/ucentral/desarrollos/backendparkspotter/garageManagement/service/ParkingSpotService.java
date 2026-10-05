package com.ucentral.desarrollos.backendparkspotter.garageManagement.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.ParkingSpot;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.event.GarageAvailabilityChangedEvent;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.ParkingSpotRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Gestión de las plazas de un garaje y de sus contadores de disponibilidad.
 * Los contadores del garaje (total/available/occupied/reserved) siempre se recalculan
 * a partir de las plazas, así nunca quedan desincronizados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParkingSpotService {

    static final String CODE_PREFIX = "P-";

    private final GarageRepository garageRepository;
    private final ParkingSpotRepository spotRepository;
    private final GarageAvailabilityAssembler availabilityAssembler;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- operaciones usadas por GarageService ----------

    /** Crea las plazas iniciales (todas libres, tipo CAR) de un garaje nuevo. */
    public void generateInitialSpots(Garage garage, int count) {
        addSpots(garage, count, SpotStatus.AVAILABLE);
        recalculateCounters(garage);
    }

    /**
     * Ajusta la cantidad de plazas al nuevo total. Al reducir, solo se eliminan plazas
     * fuera de servicio o libres; nunca una ocupada o reservada.
     */
    public void resize(Garage garage, int newTotal) {
        ensureSpots(garage);
        int difference = newTotal - garage.getSpots().size();
        if (difference > 0) {
            addSpots(garage, difference, SpotStatus.AVAILABLE);
        } else if (difference < 0) {
            removeFreeSpots(garage, -difference);
        }
        recalculateCounters(garage);
    }

    public void publishAvailability(Garage garage) {
        eventPublisher.publishEvent(new GarageAvailabilityChangedEvent(availabilityAssembler.snapshot(garage)));
    }

    // ---------- API de plazas ----------

    @Transactional(readOnly = true)
    public List<ParkingSpotResponse> list(UUID garageId, SpotStatus status, VehicleType vehicleType) {
        if (!garageRepository.existsById(garageId)) {
            throw new EntityNotFoundException("Garage not found");
        }
        return spotRepository.findByGarageFiltered(garageId, status, vehicleType).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ParkingSpotResponse add(UUID garageId, ParkingSpotRequest request, UserAccount user) {
        Garage garage = lockGarage(garageId);
        GarageAccessPolicy.assertCanManage(garage, user);
        ensureSpots(garage);

        String code = request.code() != null ? normalizeCode(request.code()) : nextCode(garage);
        assertCodeIsFree(garage, code, null);

        ParkingSpot spot = newSpot(garage, code, SpotStatus.AVAILABLE);
        if (request.floor() != null) spot.setFloor(request.floor());
        if (request.vehicleType() != null) spot.setVehicleType(request.vehicleType());
        garage.getSpots().add(spot);
        ParkingSpot saved = spotRepository.save(spot);

        recalculateCounters(garage);
        garageRepository.save(garage);
        publishAvailability(garage);
        return toResponse(saved);
    }

    @Transactional
    public ParkingSpotResponse update(UUID garageId, UUID spotId, ParkingSpotRequest request, UserAccount user) {
        Garage garage = lockGarage(garageId);
        GarageAccessPolicy.assertCanManage(garage, user);
        ParkingSpot spot = findSpot(garage, spotId);

        if (request.code() != null) {
            String code = normalizeCode(request.code());
            assertCodeIsFree(garage, code, spot.getId());
            spot.setCode(code);
        }
        if (request.floor() != null) spot.setFloor(request.floor());
        if (request.vehicleType() != null) spot.setVehicleType(request.vehicleType());

        return toResponse(spotRepository.save(spot));
    }

    /**
     * Cambio de estado en tiempo real (libre / ocupada / reservada / fuera de servicio).
     * Bloquea la fila del garaje para que los contadores sean consistentes con cambios concurrentes.
     */
    @Transactional
    public ParkingSpotResponse changeStatus(UUID garageId, UUID spotId, SpotStatus status, UserAccount user) {
        Garage garage = lockGarage(garageId);
        GarageAccessPolicy.assertCanManage(garage, user);
        ParkingSpot spot = findSpot(garage, spotId);

        if (spot.getStatus() != status) {
            spot.setStatus(status);
            recalculateCounters(garage);
            garageRepository.save(garage);
            publishAvailability(garage);
        }
        return toResponse(spot);
    }

    @Transactional
    public void delete(UUID garageId, UUID spotId, UserAccount user) {
        Garage garage = lockGarage(garageId);
        GarageAccessPolicy.assertCanManage(garage, user);
        ParkingSpot spot = findSpot(garage, spotId);

        if (spot.getStatus() == SpotStatus.OCCUPIED || spot.getStatus() == SpotStatus.RESERVED) {
            throw new ApiException("No se puede eliminar una plaza ocupada o reservada");
        }
        if (garage.getSpots().size() <= 1) {
            throw new ApiException("Un garaje debe tener al menos una plaza");
        }
        garage.getSpots().remove(spot);
        recalculateCounters(garage);
        garageRepository.save(garage);
        publishAvailability(garage);
    }

    /**
     * Los garajes creados en el Sprint 02 solo tenían contadores. Se les generan sus plazas
     * respetando cuántas estaban libres y cuántas ocupadas.
     */
    @Transactional
    public int backfillLegacyGarages() {
        List<Garage> legacy = garageRepository.findGaragesWithoutSpots();
        legacy.forEach(this::ensureSpots);
        if (!legacy.isEmpty()) {
            log.info("Se generaron plazas para {} garajes existentes", legacy.size());
        }
        return legacy.size();
    }

    // ---------- helpers ----------

    void ensureSpots(Garage garage) {
        if (!garage.getSpots().isEmpty() || garage.getTotalSpots() == null || garage.getTotalSpots() <= 0) {
            return;
        }
        int total = garage.getTotalSpots();
        int available = garage.getAvailableSpots() == null ? total : Math.clamp(garage.getAvailableSpots(), 0, total);
        addSpots(garage, available, SpotStatus.AVAILABLE);
        addSpots(garage, total - available, SpotStatus.OCCUPIED);
        recalculateCounters(garage);
    }

    void recalculateCounters(Garage garage) {
        List<ParkingSpot> spots = garage.getSpots();
        garage.setTotalSpots(spots.size());
        garage.setAvailableSpots(count(spots, SpotStatus.AVAILABLE));
        garage.setOccupiedSpots(count(spots, SpotStatus.OCCUPIED));
        garage.setReservedSpots(count(spots, SpotStatus.RESERVED));
    }

    private void addSpots(Garage garage, int count, SpotStatus status) {
        for (int i = 0; i < count; i++) {
            ParkingSpot spot = newSpot(garage, nextCode(garage), status);
            garage.getSpots().add(spot);
            // Si el garaje ya existe se persiste la plaza directamente; si es nuevo, lo hace la cascada.
            if (garage.getId() != null) {
                spotRepository.save(spot);
            }
        }
    }

    private void removeFreeSpots(Garage garage, int count) {
        List<ParkingSpot> removable = garage.getSpots().stream()
                .filter(spot -> spot.getStatus() == SpotStatus.OUT_OF_SERVICE || spot.getStatus() == SpotStatus.AVAILABLE)
                // Primero las fuera de servicio y, dentro de cada grupo, las de código más alto.
                .sorted(Comparator.comparing((ParkingSpot spot) -> spot.getStatus() != SpotStatus.OUT_OF_SERVICE)
                        .thenComparing(ParkingSpot::getCode, Comparator.reverseOrder()))
                .limit(count)
                .toList();
        if (removable.size() < count) {
            throw new ApiException("No se puede reducir la capacidad: hay plazas ocupadas o reservadas");
        }
        garage.getSpots().removeAll(removable);
    }

    private ParkingSpot newSpot(Garage garage, String code, SpotStatus status) {
        ParkingSpot spot = new ParkingSpot();
        spot.setGarage(garage);
        spot.setCode(code);
        spot.setFloor(1);
        spot.setVehicleType(VehicleType.CAR);
        spot.setStatus(status);
        return spot;
    }

    private String nextCode(Garage garage) {
        Set<String> used = garage.getSpots().stream()
                .map(spot -> spot.getCode().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        int number = 1;
        while (used.contains(formatCode(number))) {
            number++;
        }
        return formatCode(number);
    }

    private static String formatCode(int number) {
        return CODE_PREFIX + String.format("%03d", number);
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void assertCodeIsFree(Garage garage, String code, UUID ignoreSpotId) {
        boolean taken = garage.getSpots().stream()
                .anyMatch(spot -> spot.getCode().equalsIgnoreCase(code) && !Objects.equals(spot.getId(), ignoreSpotId));
        if (taken) {
            throw new ApiException("Ya existe una plaza con el código " + code + " en este garaje");
        }
    }

    private static int count(List<ParkingSpot> spots, SpotStatus status) {
        return (int) spots.stream().filter(spot -> spot.getStatus() == status).count();
    }

    private Garage lockGarage(UUID garageId) {
        return garageRepository.findByIdForUpdate(garageId)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
    }

    private ParkingSpot findSpot(Garage garage, UUID spotId) {
        return garage.getSpots().stream()
                .filter(spot -> spotId.equals(spot.getId()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Parking spot not found"));
    }

    private ParkingSpotResponse toResponse(ParkingSpot spot) {
        return new ParkingSpotResponse(
                spot.getId(),
                spot.getGarage().getId(),
                spot.getCode(),
                spot.getFloor(),
                spot.getVehicleType(),
                spot.getStatus(),
                spot.getUpdatedAt()
        );
    }
}
