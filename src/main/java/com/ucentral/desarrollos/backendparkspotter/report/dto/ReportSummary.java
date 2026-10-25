package com.ucentral.desarrollos.backendparkspotter.report.dto;

import java.math.BigDecimal;

/**
 * @param paidReservations reservas pagadas y no canceladas (confirmadas, en curso, completadas o no presentadas)
 * @param revenue          ingresos de reservas pagadas y no canceladas (las canceladas pagadas se reembolsan)
 * @param overtimeRevenue  tiempo adicional calculado en las salidas (se cobra en el garaje)
 * @param occupancyRate    minutos reservados y pagados / (plazas × minutos del rango), de 0 a 1
 */
public record ReportSummary(
        long totalReservations,
        long paidReservations,
        long completed,
        long cancelled,
        long expired,
        long noShow,
        BigDecimal revenue,
        BigDecimal overtimeRevenue,
        double occupancyRate,
        double averageRating
) {
}
