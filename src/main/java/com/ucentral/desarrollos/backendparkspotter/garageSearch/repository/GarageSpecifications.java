package com.ucentral.desarrollos.backendparkspotter.garageSearch.repository;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.ParkingSpot;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.service.GeoUtils.BoundingBox;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Filtros de búsqueda como Specifications de JPA: cada filtro se traduce a SQL y se resuelve
 * en la base de datos (aprovechando los índices de garages y parking_spots), no en memoria.
 */
public final class GarageSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private GarageSpecifications() {
    }

    public static Specification<Garage> isActive() {
        return (root, query, cb) -> cb.equal(root.get("status"), GarageStatus.ACTIVE);
    }

    /** Texto libre sobre nombre, dirección y ciudad (sin distinguir mayúsculas). */
    public static Specification<Garage> textMatches(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("addressLine")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("city")), pattern, LIKE_ESCAPE)
        );
    }

    public static Specification<Garage> cityEquals(String city) {
        String normalized = city.trim().toLowerCase(Locale.ROOT);
        return (root, query, cb) -> cb.equal(cb.lower(root.get("city")), normalized);
    }

    public static Specification<Garage> priceAtLeast(BigDecimal min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("pricePerHour"), min);
    }

    public static Specification<Garage> priceAtMost(BigDecimal max) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("pricePerHour"), max);
    }

    public static Specification<Garage> open24Hours(boolean value) {
        return (root, query, cb) -> cb.equal(root.get("open24Hours"), value);
    }

    public static Specification<Garage> minAvailableSpots(int min) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("availableSpots"), min);
    }

    /**
     * Abierto a la hora indicada. Misma regla que OpeningHours, incluido el horario nocturno
     * (apertura mayor que cierre, ej. 18:00 - 06:00).
     */
    public static Specification<Garage> openAt(LocalTime time) {
        return (root, query, cb) -> {
            Path<LocalTime> opening = root.get("openingTime");
            Path<LocalTime> closing = root.get("closingTime");
            Predicate sameDay = cb.and(
                    cb.lessThan(opening, closing),
                    cb.lessThanOrEqualTo(opening, time),
                    cb.greaterThan(closing, time));
            Predicate overnight = cb.and(
                    cb.greaterThan(opening, closing),
                    cb.or(cb.lessThanOrEqualTo(opening, time), cb.greaterThan(closing, time)));
            Predicate sameOpeningAndClosing = cb.equal(opening, closing);
            return cb.or(cb.isTrue(root.get("open24Hours")), sameDay, overnight, sameOpeningAndClosing);
        };
    }

    /**
     * El garaje tiene plazas del tipo indicado; si onlyAvailable, además alguna está libre.
     * Se resuelve con EXISTS sobre parking_spots (índice garage_id, vehicle_type, status).
     */
    public static Specification<Garage> hasSpotsFor(VehicleType vehicleType, boolean onlyAvailable) {
        return (root, query, cb) -> {
            Subquery<UUID> subquery = query.subquery(UUID.class);
            Root<ParkingSpot> spot = subquery.from(ParkingSpot.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(spot.get("garage"), root));
            predicates.add(cb.equal(spot.get("vehicleType"), vehicleType));
            if (onlyAvailable) {
                predicates.add(cb.equal(spot.get("status"), SpotStatus.AVAILABLE));
            }
            subquery.select(spot.get("id")).where(predicates.toArray(Predicate[]::new));
            return cb.exists(subquery);
        };
    }

    /** Rectángulo de coordenadas: filtro barato e indexable antes de calcular distancias exactas. */
    public static Specification<Garage> withinBounds(BoundingBox box) {
        return (root, query, cb) -> cb.and(
                cb.between(root.get("latitude"), box.minLat(), box.maxLat()),
                cb.between(root.get("longitude"), box.minLng(), box.maxLng()));
    }

    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
