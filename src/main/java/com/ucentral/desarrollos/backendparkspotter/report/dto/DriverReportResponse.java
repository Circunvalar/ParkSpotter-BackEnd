package com.ucentral.desarrollos.backendparkspotter.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Reporte personal del conductor.
 *
 * @param paidReservations  reservas pagadas y no canceladas
 * @param upcoming          confirmadas que todavía no empiezan
 * @param totalSpent        suma de lo pagado en línea (reservas pagadas y no canceladas)
 * @param overtimeAmount    tiempo extra calculado en las salidas (se paga en el garaje)
 * @param parkedMinutes     minutos entre entrada y salida registradas
 * @param garagesUsed       garajes distintos con al menos una reserva pagada
 * @param frequentGarages   hasta 5 garajes con más reservas pagadas
 * @param daily             un punto por día del rango (reservas y gasto)
 */
public record DriverReportResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        long totalReservations,
        long paidReservations,
        long completed,
        long cancelled,
        long expired,
        long noShow,
        long upcoming,
        BigDecimal totalSpent,
        BigDecimal overtimeAmount,
        long parkedMinutes,
        long garagesUsed,
        List<FrequentGarage> frequentGarages,
        List<DailyPoint> daily
) {

    public record FrequentGarage(UUID garageId, String name, long reservations, BigDecimal spent) {
    }
}
