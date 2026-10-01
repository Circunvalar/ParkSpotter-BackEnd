package com.ucentral.desarrollos.backendparkspotter.garageManagement.repository;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.SpotCount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.ParkingSpot;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, UUID> {

    Optional<ParkingSpot> findByIdAndGarageId(UUID id, UUID garageId);

    boolean existsByGarageIdAndCodeIgnoreCase(UUID garageId, String code);

    /**
     * Listado de plazas de un garaje con filtros opcionales (null = sin filtro).
     */
    @Query("""
            SELECT s FROM ParkingSpot s
            WHERE s.garage.id = :garageId
              AND (:status IS NULL OR s.status = :status)
              AND (:vehicleType IS NULL OR s.vehicleType = :vehicleType)
            ORDER BY s.floor ASC, s.code ASC
            """)
    List<ParkingSpot> findByGarageFiltered(@Param("garageId") UUID garageId,
                                           @Param("status") SpotStatus status,
                                           @Param("vehicleType") VehicleType vehicleType);

    @Query("SELECT DISTINCT s.floor FROM ParkingSpot s WHERE s.garage.id = :garageId ORDER BY s.floor")
    List<Integer> findFloorsByGarageId(@Param("garageId") UUID garageId);

    /**
     * Conteo agrupado por tipo de vehículo y estado: una sola consulta para el resumen del detalle.
     */
    @Query("""
            SELECT new com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.SpotCount(
                s.vehicleType, s.status, COUNT(s))
            FROM ParkingSpot s
            WHERE s.garage.id = :garageId
            GROUP BY s.vehicleType, s.status
            """)
    List<SpotCount> countByVehicleTypeAndStatus(@Param("garageId") UUID garageId);
}
