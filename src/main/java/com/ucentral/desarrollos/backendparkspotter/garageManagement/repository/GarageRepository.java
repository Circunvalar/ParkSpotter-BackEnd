package com.ucentral.desarrollos.backendparkspotter.garageManagement.repository;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GarageRepository extends JpaRepository<Garage, UUID>, JpaSpecificationExecutor<Garage> {

    /**
     * Bloquea la fila del garaje mientras se cambian estados de plazas, para que dos cambios
     * simultáneos no pisen los contadores de disponibilidad.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Garage g WHERE g.id = :id")
    Optional<Garage> findByIdForUpdate(@Param("id") UUID id);

    /** Garajes creados antes de existir las plazas (Sprint 02), para generarlas una única vez. */
    @Query("SELECT g FROM Garage g WHERE g.spots IS EMPTY AND g.totalSpots > 0")
    List<Garage> findGaragesWithoutSpots();

    List<Garage> findByStatus(GarageStatus status);

    List<Garage> findByOwnerId(UUID ownerId);

    List<Garage> findByCityIgnoreCaseAndStatus(String city, GarageStatus status);

    /**
     * Búsqueda de garajes cercanos a un punto (lat/lng) dentro de un radio en kilómetros,
     * usando la fórmula de Haversine directamente en la consulta. Solo devuelve garajes ACTIVE.
     * Ordena por distancia ascendente.
     */
    @Query(value = """
            SELECT * FROM (
                SELECT g.*,
                       (6371 * acos(
                            LEAST(1.0, GREATEST(-1.0,
                                cos(radians(:lat)) * cos(radians(g.latitude)) *
                                cos(radians(g.longitude) - radians(:lng)) +
                                sin(radians(:lat)) * sin(radians(g.latitude))
                            ))
                       )) AS distance_km
                FROM garages g
                WHERE g.status = 'ACTIVE'
            ) nearby
            WHERE nearby.distance_km <= :radiusKm
            ORDER BY nearby.distance_km ASC
            """, nativeQuery = true)
    List<Garage> findNearby(@Param("lat") double latitude,
                             @Param("lng") double longitude,
                             @Param("radiusKm") double radiusKm);
}
