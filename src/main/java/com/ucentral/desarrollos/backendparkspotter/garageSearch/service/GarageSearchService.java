package com.ucentral.desarrollos.backendparkspotter.garageSearch.service;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.SpotCount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.ParkingSpot;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.ParkingSpotRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageAvailabilityAssembler;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.OpeningHours;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageDetailResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageMapResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageMarkerResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSearchCriteria;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSortOption;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSummaryResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.MapBoundsRequest;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.VehicleTypeAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.repository.GarageSpecifications;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.service.GeoUtils.BoundingBox;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Búsqueda, listado, mapa y detalle de garajes para los fronts web y Android.
 * Solo se exponen garajes ACTIVE en búsqueda y mapa.
 */
@Service
@RequiredArgsConstructor
public class GarageSearchService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final double DEFAULT_RADIUS_KM = 5.0;
    static final int DEFAULT_MAP_LIMIT = 200;
    /** Tope de candidatos dentro del área para la búsqueda por distancia (protege tiempos de respuesta). */
    static final int MAX_GEO_CANDIDATES = 1000;

    private final GarageRepository garageRepository;
    private final ParkingSpotRepository spotRepository;
    private final GarageAvailabilityAssembler availabilityAssembler;

    // ---------- búsqueda paginada ----------

    @Transactional(readOnly = true)
    public PageResponse<GarageSummaryResponse> search(GarageSearchCriteria criteria) {
        validate(criteria);
        int page = criteria.page() == null ? 0 : criteria.page();
        int size = criteria.size() == null ? DEFAULT_PAGE_SIZE : criteria.size();
        LocalTime now = availabilityAssembler.now();
        Specification<Garage> spec = buildFilters(criteria, now);

        if (criteria.hasLocation()) {
            return searchByDistance(criteria, spec, page, size, now);
        }

        GarageSortOption sortOption = criteria.sort() == null ? GarageSortOption.NAME : criteria.sort();
        Page<Garage> result = garageRepository.findAll(spec, PageRequest.of(page, size, toSort(sortOption)));
        return PageResponse.of(result, garage -> toSummary(garage, now, null));
    }

    /**
     * Con coordenadas: la base de datos filtra por el rectángulo que contiene el radio (indexado)
     * y aquí se calcula la distancia exacta, se descartan los de fuera del círculo, se ordena y se pagina.
     */
    private PageResponse<GarageSummaryResponse> searchByDistance(GarageSearchCriteria criteria,
                                                                 Specification<Garage> spec,
                                                                 int page, int size, LocalTime now) {
        double lat = criteria.lat();
        double lng = criteria.lng();
        double radiusKm = criteria.radiusKm() == null ? DEFAULT_RADIUS_KM : criteria.radiusKm();
        BoundingBox box = GeoUtils.boundingBox(lat, lng, radiusKm);

        List<Garage> candidates = garageRepository
                .findAll(spec.and(GarageSpecifications.withinBounds(box)), PageRequest.of(0, MAX_GEO_CANDIDATES))
                .getContent();

        List<GarageSummaryResponse> inRadius = candidates.stream()
                .map(garage -> toSummary(garage, now,
                        GeoUtils.roundKm(GeoUtils.haversineKm(lat, lng, garage.getLatitude(), garage.getLongitude()))))
                .filter(summary -> summary.distanceKm() <= radiusKm)
                .sorted(distanceAwareComparator(criteria.sort() == null ? GarageSortOption.DISTANCE : criteria.sort()))
                .toList();

        int from = Math.min(page * size, inRadius.size());
        int to = Math.min(from + size, inRadius.size());
        return PageResponse.of(inRadius.subList(from, to), page, size, inRadius.size());
    }

    // ---------- mapa ----------

    @Transactional(readOnly = true)
    public GarageMapResponse mapMarkers(MapBoundsRequest bounds, GarageSearchCriteria filters) {
        if (bounds.minLat() > bounds.maxLat() || bounds.minLng() > bounds.maxLng()) {
            throw new ApiException("Los límites del mapa no son válidos: min debe ser menor o igual que max");
        }
        int limit = bounds.limit() == null ? DEFAULT_MAP_LIMIT : bounds.limit();
        LocalTime now = availabilityAssembler.now();
        BoundingBox box = new BoundingBox(bounds.minLat(), bounds.maxLat(), bounds.minLng(), bounds.maxLng());
        Specification<Garage> spec = buildFilters(filters, now).and(GarageSpecifications.withinBounds(box));

        // Primero los que tienen más plazas libres: si se trunca, se pierden los menos útiles.
        Page<Garage> result = garageRepository.findAll(spec,
                PageRequest.of(0, limit, Sort.by(Sort.Order.desc("availableSpots"), Sort.Order.asc("id"))));
        List<GarageMarkerResponse> markers = result.getContent().stream()
                .map(garage -> toMarker(garage, now))
                .toList();
        return new GarageMapResponse(markers, markers.size(), result.getTotalElements() > markers.size());
    }

    // ---------- detalle y disponibilidad ----------

    @Transactional(readOnly = true)
    public GarageDetailResponse detail(UUID garageId, boolean includeSpots) {
        Garage garage = findGarage(garageId);
        List<ParkingSpotResponse> spots = includeSpots
                ? spotRepository.findByGarageFiltered(garageId, null, null).stream().map(this::toSpotResponse).toList()
                : List.of();
        return new GarageDetailResponse(
                garage.getId(),
                garage.getName(),
                garage.getDescription(),
                garage.getPhone(),
                garage.getAddressLine(),
                garage.getCity(),
                garage.getState(),
                garage.getCountry(),
                garage.getPostalCode(),
                garage.getLatitude(),
                garage.getLongitude(),
                garage.getPricePerHour(),
                garage.isOpen24Hours(),
                garage.getOpeningTime(),
                garage.getClosingTime(),
                availabilityAssembler.snapshot(garage),
                availabilityByVehicleType(spotRepository.countByVehicleTypeAndStatus(garageId)),
                spotRepository.findFloorsByGarageId(garageId),
                spots,
                garage.getCreatedAt(),
                garage.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public GarageAvailabilityResponse availability(UUID garageId) {
        return availabilityAssembler.snapshot(findGarage(garageId));
    }

    // ---------- helpers ----------

    private void validate(GarageSearchCriteria criteria) {
        if ((criteria.lat() == null) != (criteria.lng() == null)) {
            throw new ApiException("lat y lng deben enviarse juntos");
        }
        if (criteria.radiusKm() != null && !criteria.hasLocation()) {
            throw new ApiException("radiusKm requiere lat y lng");
        }
        if (criteria.sort() == GarageSortOption.DISTANCE && !criteria.hasLocation()) {
            throw new ApiException("sort=DISTANCE requiere lat y lng");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new ApiException("minPrice no puede ser mayor que maxPrice");
        }
    }

    Specification<Garage> buildFilters(GarageSearchCriteria criteria, LocalTime now) {
        List<Specification<Garage>> filters = new ArrayList<>();
        filters.add(GarageSpecifications.isActive());
        if (hasText(criteria.q())) filters.add(GarageSpecifications.textMatches(criteria.q()));
        if (hasText(criteria.city())) filters.add(GarageSpecifications.cityEquals(criteria.city()));
        if (criteria.minPrice() != null) filters.add(GarageSpecifications.priceAtLeast(criteria.minPrice()));
        if (criteria.maxPrice() != null) filters.add(GarageSpecifications.priceAtMost(criteria.maxPrice()));
        if (criteria.open24Hours() != null) filters.add(GarageSpecifications.open24Hours(criteria.open24Hours()));
        if (Boolean.TRUE.equals(criteria.openNow())) filters.add(GarageSpecifications.openAt(now));

        boolean onlyAvailable = Boolean.TRUE.equals(criteria.onlyAvailable());
        if (criteria.vehicleType() != null) {
            filters.add(GarageSpecifications.hasSpotsFor(criteria.vehicleType(), onlyAvailable));
        } else if (onlyAvailable) {
            filters.add(GarageSpecifications.minAvailableSpots(1));
        }
        if (criteria.minAvailableSpots() != null) {
            filters.add(GarageSpecifications.minAvailableSpots(criteria.minAvailableSpots()));
        }
        return Specification.allOf(filters);
    }

    private static Sort toSort(GarageSortOption option) {
        Sort sort = switch (option) {
            case PRICE_ASC -> Sort.by(Sort.Order.asc("pricePerHour"));
            case PRICE_DESC -> Sort.by(Sort.Order.desc("pricePerHour"));
            case AVAILABILITY -> Sort.by(Sort.Order.desc("availableSpots"));
            case NEWEST -> Sort.by(Sort.Order.desc("createdAt"));
            case NAME, DISTANCE -> Sort.by(Sort.Order.asc("name"));
        };
        // Desempate estable para que la paginación no repita ni salte resultados.
        return sort.and(Sort.by(Sort.Order.asc("id")));
    }

    private static Comparator<GarageSummaryResponse> distanceAwareComparator(GarageSortOption option) {
        Comparator<GarageSummaryResponse> byDistance = Comparator.comparing(GarageSummaryResponse::distanceKm);
        Comparator<GarageSummaryResponse> primary = switch (option) {
            case PRICE_ASC -> Comparator.comparing(GarageSummaryResponse::pricePerHour);
            case PRICE_DESC -> Comparator.comparing(GarageSummaryResponse::pricePerHour).reversed();
            case AVAILABILITY -> Comparator.comparingInt(GarageSummaryResponse::availableSpots).reversed();
            case NAME -> Comparator.comparing(GarageSummaryResponse::name, String.CASE_INSENSITIVE_ORDER);
            case NEWEST, DISTANCE -> byDistance;
        };
        return primary.thenComparing(byDistance).thenComparing(summary -> summary.id().toString());
    }

    private static List<VehicleTypeAvailability> availabilityByVehicleType(List<SpotCount> counts) {
        Map<VehicleType, int[]> byType = new EnumMap<>(VehicleType.class);
        for (SpotCount count : counts) {
            // [total, available, occupied, reserved, outOfService]
            int[] totals = byType.computeIfAbsent(count.vehicleType(), type -> new int[5]);
            int value = count.count().intValue();
            totals[0] += value;
            totals[1 + count.status().ordinal()] += value;
        }
        return byType.entrySet().stream()
                .map(entry -> new VehicleTypeAvailability(entry.getKey(),
                        entry.getValue()[0],
                        entry.getValue()[1 + SpotStatus.AVAILABLE.ordinal()],
                        entry.getValue()[1 + SpotStatus.OCCUPIED.ordinal()],
                        entry.getValue()[1 + SpotStatus.RESERVED.ordinal()],
                        entry.getValue()[1 + SpotStatus.OUT_OF_SERVICE.ordinal()]))
                .toList();
    }

    private Garage findGarage(UUID garageId) {
        return garageRepository.findById(garageId)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private GarageSummaryResponse toSummary(Garage garage, LocalTime now, Double distanceKm) {
        return new GarageSummaryResponse(
                garage.getId(),
                garage.getName(),
                garage.getAddressLine(),
                garage.getCity(),
                garage.getLatitude(),
                garage.getLongitude(),
                garage.getPricePerHour(),
                garage.getTotalSpots(),
                garage.getAvailableSpots(),
                garage.getOccupiedSpots(),
                garage.getReservedSpots(),
                garage.getAvailability(),
                garage.isOpen24Hours(),
                garage.getOpeningTime(),
                garage.getClosingTime(),
                OpeningHours.isOpenAt(garage, now),
                distanceKm
        );
    }

    private GarageMarkerResponse toMarker(Garage garage, LocalTime now) {
        return new GarageMarkerResponse(
                garage.getId(),
                garage.getName(),
                garage.getLatitude(),
                garage.getLongitude(),
                garage.getPricePerHour(),
                garage.getAvailableSpots(),
                garage.getTotalSpots(),
                garage.getAvailability(),
                OpeningHours.isOpenAt(garage, now)
        );
    }

    private ParkingSpotResponse toSpotResponse(ParkingSpot spot) {
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
