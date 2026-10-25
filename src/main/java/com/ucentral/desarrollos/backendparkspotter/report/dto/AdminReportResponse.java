package com.ucentral.desarrollos.backendparkspotter.report.dto;

import java.time.LocalDate;
import java.util.List;

/** Reporte general del administrador (Sprint 06, PB-34). */
public record AdminReportResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        Users users,
        Garages garages,
        ReportSummary summary,
        Double averageSearchToReservationSeconds,
        List<GarageReport> topGarages,
        List<DailyPoint> daily
) {

    public record Users(long total, long drivers, long owners, long admins, long disabled, long newInRange) {
    }

    public record Garages(long total, long active, long verified, long pendingVerification) {
    }
}
