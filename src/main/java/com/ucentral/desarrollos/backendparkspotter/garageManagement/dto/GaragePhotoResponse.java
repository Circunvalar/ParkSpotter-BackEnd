package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import java.time.Instant;
import java.util.UUID;

/** @param url ruta relativa de la imagen: GET {API}{url} devuelve los bytes (pública, cacheable) */
public record GaragePhotoResponse(
        UUID id,
        UUID garageId,
        String contentType,
        int sizeBytes,
        String url,
        Instant createdAt
) {

    public GaragePhotoResponse(UUID id, UUID garageId, String contentType, Integer sizeBytes, Instant createdAt) {
        this(id, garageId, contentType, sizeBytes, "/api/v1/garages/" + garageId + "/photos/" + id, createdAt);
    }
}
