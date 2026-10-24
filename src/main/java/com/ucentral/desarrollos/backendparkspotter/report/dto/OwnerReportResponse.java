package com.ucentral.desarrollos.backendparkspotter.report.dto;

import java.time.LocalDate;
import java.util.List;

/** Reporte del propietario (Sprint 06, PB-25). Fechas inclusivas en la zona horaria de los garajes. */
public record OwnerReportResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        ReportSummary summary,
        List<GarageReport> garages,
        List<DailyPoint> daily
) {
}
