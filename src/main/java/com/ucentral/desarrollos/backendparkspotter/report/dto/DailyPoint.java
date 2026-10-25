package com.ucentral.desarrollos.backendparkspotter.report.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPoint(LocalDate date, long reservations, BigDecimal revenue) {
}
