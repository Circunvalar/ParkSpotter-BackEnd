package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record GarageResponse(
        UUID id,
        String name,
        String description,
        String phone,

        UUID ownerId,
        String ownerEmail,

        String addressLine,
        String city,
        String state,
        String country,
        String postalCode,

        Double latitude,
        Double longitude,

        Integer totalSpots,
        Integer availableSpots,
        BigDecimal pricePerHour,

        boolean open24Hours,
        LocalTime openingTime,
        LocalTime closingTime,

        GarageStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
