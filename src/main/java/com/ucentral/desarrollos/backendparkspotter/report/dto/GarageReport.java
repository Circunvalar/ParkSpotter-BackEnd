package com.ucentral.desarrollos.backendparkspotter.report.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GarageReport(
        UUID garageId,
        String name,
        int totalSpots,
        long reservations,
        long completed,
        long cancelled,
        long noShow,
        BigDecimal revenue,
        double occupancyRate,
        double averageRating,
        int ratingCount
) {
}
