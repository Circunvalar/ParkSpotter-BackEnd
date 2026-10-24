package com.ucentral.desarrollos.backendparkspotter.report.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.RoleNames;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VerificationStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.report.dto.AdminReportResponse;
import com.ucentral.desarrollos.backendparkspotter.report.dto.DailyPoint;
import com.ucentral.desarrollos.backendparkspotter.report.dto.GarageReport;
import com.ucentral.desarrollos.backendparkspotter.report.dto.OwnerReportResponse;
import com.ucentral.desarrollos.backendparkspotter.report.dto.ReportSummary;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Reportes del propietario y del administrador (Sprint 06, T6-04). Se calculan en memoria sobre las
 * reservas cuya franja empieza en el rango: para el volumen del proyecto es simple y suficientemente rápido.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    static final String CURRENCY = "COP";

    private final ReservationRepository reservationRepository;
    private final GarageRepository garageRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public OwnerReportResponse ownerReport(UserAccount owner, LocalDate from, LocalDate to, UUID garageId) {
        Range range = range(from, to);
        List<Garage> garages = garageRepository.findByOwnerId(owner.getId());
        if (garageId != null) {
            garages = garages.stream().filter(g -> g.getId().equals(garageId)).toList();
            if (garages.isEmpty()) {
                throw new AccessDeniedException("No tienes permisos sobre este garaje");
            }
        }
        List<UUID> ids = garages.stream().map(Garage::getId).toList();
        List<Reservation> reservations = ids.isEmpty()
                ? List.of()
                : reservationRepository.findForReport(ids, range.start(), range.end());
        return new OwnerReportResponse(range.from(), range.to(), CURRENCY,
                summary(reservations, garages, range),
                garages.stream().map(g -> garageReport(g, reservations, range))
                        .sorted(Comparator.comparing(GarageReport::revenue).reversed()).toList(),
                daily(reservations, range));
    }

    @Transactional(readOnly = true)
    public AdminReportResponse adminReport(LocalDate from, LocalDate to) {
        Range range = range(from, to);
        List<Garage> garages = garageRepository.findAll();
        List<Reservation> reservations = reservationRepository.findAllForReport(range.start(), range.end());
        AdminReportResponse.Users users = new AdminReportResponse.Users(
                userRepository.countByDeletedAtIsNull(),
                userRepository.countActiveByRole(RoleNames.DRIVER),
                userRepository.countActiveByRole(RoleNames.OWNER),
                userRepository.countActiveByRole(RoleNames.ADMIN),
                userRepository.countByDeletedAtIsNullAndEnabledFalse(),
                userRepository.countByDeletedAtIsNullAndCreatedAtBetween(range.start(), range.end()));
        AdminReportResponse.Garages garageStats = new AdminReportResponse.Garages(
                garages.size(),
                garageRepository.countByStatus(GarageStatus.ACTIVE),
                garageRepository.countByVerificationStatus(VerificationStatus.VERIFIED),
                garageRepository.countByVerificationStatus(VerificationStatus.PENDING));
        OptionalDouble searchSeconds = reservations.stream()
                .map(Reservation::getSearchToReservationSeconds)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average();
        List<GarageReport> top = garages.stream()
                .map(g -> garageReport(g, reservations, range))
                .filter(report -> report.reservations() > 0)
                .sorted(Comparator.comparing(GarageReport::revenue).reversed().thenComparing(GarageReport::reservations, Comparator.reverseOrder()))
                .limit(5)
                .toList();
        return new AdminReportResponse(range.from(), range.to(), CURRENCY, users, garageStats,
                summary(reservations, garages, range),
                searchSeconds.isPresent() ? round(searchSeconds.getAsDouble(), 1) : null,
                top, daily(reservations, range));
    }

    // ---------- cálculo ----------

    private ReportSummary summary(List<Reservation> reservations, Collection<Garage> garages, Range range) {
        int totalSpots = garages.stream().mapToInt(Garage::getTotalSpots).sum();
        List<Garage> rated = garages.stream().filter(g -> g.getRatingCount() > 0).toList();
        double averageRating = rated.isEmpty() ? 0.0
                : round(rated.stream().mapToDouble(g -> g.getAverageRating() * g.getRatingCount()).sum()
                / rated.stream().mapToInt(Garage::getRatingCount).sum(), 2);
        return new ReportSummary(
                reservations.size(),
                count(reservations, ReportService::isPaid),
                count(reservations, r -> r.getStatus() == ReservationStatus.COMPLETED),
                count(reservations, r -> r.getStatus() == ReservationStatus.CANCELLED),
                count(reservations, r -> r.getStatus() == ReservationStatus.EXPIRED),
                count(reservations, r -> r.getStatus() == ReservationStatus.NO_SHOW),
                revenue(reservations),
                reservations.stream().map(Reservation::getOvertimeAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                occupancy(reservations, totalSpots, range),
                averageRating);
    }

    private GarageReport garageReport(Garage garage, List<Reservation> all, Range range) {
        List<Reservation> mine = all.stream().filter(r -> r.getGarage().getId().equals(garage.getId())).toList();
        return new GarageReport(
                garage.getId(),
                garage.getName(),
                garage.getTotalSpots(),
                mine.size(),
                count(mine, r -> r.getStatus() == ReservationStatus.COMPLETED),
                count(mine, r -> r.getStatus() == ReservationStatus.CANCELLED),
                count(mine, r -> r.getStatus() == ReservationStatus.NO_SHOW),
                revenue(mine),
                occupancy(mine, garage.getTotalSpots(), range),
                garage.getAverageRating(),
                garage.getRatingCount());
    }

    private List<DailyPoint> daily(List<Reservation> reservations, Range range) {
        Map<LocalDate, List<Reservation>> byDay = new LinkedHashMap<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            byDay.put(day, new java.util.ArrayList<>());
        }
        reservations.forEach(r -> byDay.computeIfAbsent(LocalDate.ofInstant(r.getStartTime(), range.zone()),
                d -> new java.util.ArrayList<>()).add(r));
        return byDay.entrySet().stream()
                .map(e -> new DailyPoint(e.getKey(), e.getValue().size(), revenue(e.getValue())))
                .toList();
    }

    /** Pagadas y no canceladas: confirmadas, en curso, completadas o no presentadas. */
    static boolean isPaid(Reservation r) {
        return r.getPaidAt() != null && r.getStatus() != ReservationStatus.CANCELLED;
    }

    private static BigDecimal revenue(List<Reservation> reservations) {
        return reservations.stream().filter(ReportService::isPaid)
                .map(Reservation::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static double occupancy(List<Reservation> reservations, int totalSpots, Range range) {
        long capacity = (long) totalSpots * Duration.between(range.start(), range.end()).toMinutes();
        if (capacity <= 0) {
            return 0.0;
        }
        long used = reservations.stream().filter(ReportService::isPaid)
                .mapToLong(r -> Math.max(0, Duration.between(
                        max(r.getStartTime(), range.start()), min(r.getEndTime(), range.end())).toMinutes()))
                .sum();
        return round(Math.min(1.0, (double) used / capacity), 4);
    }

    private static long count(List<Reservation> reservations, Predicate<Reservation> predicate) {
        return reservations.stream().filter(predicate).count();
    }

    private Range range(LocalDate from, LocalDate to) {
        ZoneId zone = clock.getZone();
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end)) {
            throw new ApiException("from no puede ser posterior a to");
        }
        if (ChronoUnit.DAYS.between(start, end) >= MAX_DAYS) {
            throw new ApiException("El rango del reporte no puede superar " + MAX_DAYS + " días");
        }
        return new Range(start, end, zone, start.atStartOfDay(zone).toInstant(), end.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static Instant max(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private static Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    private record Range(LocalDate from, LocalDate to, ZoneId zone, Instant start, Instant end) {
    }
}
