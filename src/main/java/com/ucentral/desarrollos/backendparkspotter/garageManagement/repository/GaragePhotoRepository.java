package com.ucentral.desarrollos.backendparkspotter.garageManagement.repository;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GaragePhotoResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GaragePhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GaragePhotoRepository extends JpaRepository<GaragePhoto, UUID> {

    /** Solo los metadatos (sin los bytes de la imagen). */
    @Query("""
            SELECT new com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GaragePhotoResponse(
                p.id, p.garage.id, p.contentType, p.sizeBytes, p.createdAt)
            FROM GaragePhoto p WHERE p.garage.id = :garageId ORDER BY p.createdAt ASC, p.id ASC
            """)
    List<GaragePhotoResponse> findMetadataByGarageId(@Param("garageId") UUID garageId);

    long countByGarageId(UUID garageId);

    Optional<GaragePhoto> findByIdAndGarageId(UUID id, UUID garageId);
}
