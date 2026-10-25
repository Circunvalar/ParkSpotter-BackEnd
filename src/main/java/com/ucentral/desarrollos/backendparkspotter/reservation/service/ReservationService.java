package com.ucentral.desarrollos.backendparkspotter.reservation.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.ParkingSpot;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageAccessPolicy;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.ReservationQuoteResponse;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.ReservationRequest;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.ReservationResponse;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationCancelledEvent;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import com.ucentral.desarrollos.backendparkspotter.shared.security.SecureTokens;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Reservas (Sprint 04-05): crear con bloqueo concurrente de la plaza, cancelar, vencer, entrada y salida
 * con el código de reserva e historial.
 *
 * Concurrencia: toda operación que asigna o libera plazas bloquea primero la fila del garaje
 * (SELECT ... FOR UPDATE), igual que los cambios de estado de plazas del Sprint 03. Así dos conductores
 * que reservan a la vez en el mismo garaje se atienden uno detrás del otro y nunca reciben la misma plaza.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    static final String CURRENCY = "COP";
    static final String CODE_PREFIX = "PS-";
    private static final int CODE_LENGTH = 6;
    private static final long MAX_SEARCH_SECONDS = Duration.ofDays(1).toSeconds();

    private final ReservationRepository reservationRepository;
    private final GarageRepository garageRepository;
    private final ParkingSpotService parkingSpotService;
    private final ReservationPolicy policy;
    private final ReservationNotifier notifier;
    private final ApplicationEventPublisher eventPublisher;

    // ---------- cotización ----------

    @Transactional(readOnly = true)
    public ReservationQuoteResponse quote(UUID garageId, VehicleType vehicleType, Instant start, Instant end) {
        Garage garage = garageRepository.findById(garageId)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
        long minutes = end.isAfter(start) ? Duration.between(start, end).toMinutes() : 0;
        String reason = null;
        int availableSpots = 0;
        try {
            policy.validateSlot(start, end);
            policy.validateGarage(garage, start, end);
            availableSpots = candidateSpots(garage, vehicleType, start, end).size();
            if (availableSpots == 0) {
                reason = "No hay plazas libres para ese tipo de vehículo en esa franja";
            }
        } catch (ApiException ex) {
            reason = ex.getMessage();
        }
        return new ReservationQuoteResponse(garageId, vehicleType, start, end, minutes, garage.getPricePerHour(),
                policy.priceForMinutes(garage.getPricePerHour(), minutes), CURRENCY, availableSpots, reason == null, reason);
    }

    // ---------- crear ----------

    @Transactional
    public ReservationResponse create(ReservationRequest request, UserAccount driver) {
        Instant start = request.startTime();
        Instant end = request.endTime();
        policy.validateSlot(start, end);

        Garage garage = garageRepository.findByIdForUpdate(request.garageId())
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
        policy.validateGarage(garage, start, end);
        if (reservationRepository.driverHasOverlap(driver.getId(), start, end, ReservationStatus.ACTIVE)) {
            throw new ConflictException("Ya tienes una reserva activa que se cruza con esa franja");
        }

        List<ParkingSpot> candidates = candidateSpots(garage, request.vehicleType(), start, end);
        ParkingSpot spot = pickSpot(candidates, request.spotId());

        Reservation reservation = new Reservation();
        reservation.setCode(newCode());
        reservation.setDriver(driver);
        reservation.setGarage(garage);
        reservation.setSpot(spot);
        reservation.setVehicleType(request.vehicleType());
        reservation.setVehiclePlate(normalizePlate(request.vehiclePlate()));
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setPricePerHour(garage.getPricePerHour());
        reservation.setTotalAmount(policy.price(garage.getPricePerHour(), start, end));
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);
        reservation.setPaymentDeadline(policy.now().plus(policy.getPaymentTimeout()));
        reservation.setSearchToReservationSeconds(searchSeconds(request.searchStartedAt()));

        if (policy.shouldHoldSpot(start)) {
            holdSpot(reservation, garage, spot);
        }
        Reservation saved = reservationRepository.save(reservation);
        notifier.created(saved);
        return toResponse(saved);
    }

    // ---------- consultar ----------

    @Transactional(readOnly = true)
    public ReservationResponse get(UUID id, UserAccount user) {
        Reservation reservation = findReservation(id);
        assertCanView(reservation, user);
        return toResponse(reservation);
    }

    /** Historial del conductor (T5-01), más recientes primero. */
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> listMine(UserAccount driver, ReservationStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Reservation> result = status == null
                ? reservationRepository.findByDriverIdOrderByStartTimeDesc(driver.getId(), pageable)
                : reservationRepository.findByDriverIdAndStatusOrderByStartTimeDesc(driver.getId(), status, pageable);
        return PageResponse.of(result, this::toResponse);
    }

    /**
     * Reservas de los garajes del propietario (panel de entradas y salidas del día, T5-15).
     * date filtra por el día de inicio en la zona horaria de los garajes. Un administrador ve todos los garajes.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> listOwned(UserAccount user, LocalDate date, UUID garageId,
                                                       ReservationStatus status, int page, int size) {
        ZoneId zone = policy.getClock().getZone();
        Specification<Reservation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!user.isAdmin()) {
                predicates.add(cb.equal(root.get("garage").get("owner").get("id"), user.getId()));
            }
            if (garageId != null) {
                predicates.add(cb.equal(root.get("garage").get("id"), garageId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (date != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startTime"), date.atStartOfDay(zone).toInstant()));
                predicates.add(cb.lessThan(root.get("startTime"), date.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("startTime"), Sort.Order.asc("id")));
        return PageResponse.of(reservationRepository.findAll(spec, pageable), this::toResponse);
    }

    // ---------- cancelar ----------

    /** Cancela y libera la plaza (T4-05). Puede hacerlo el conductor, el dueño del garaje o un administrador. */
    @Transactional
    public ReservationResponse cancel(UUID id, UserAccount user, String reason) {
        Locked locked = lock(id);
        Reservation reservation = locked.reservation();
        Garage garage = locked.garage();
        boolean byDriver = reservation.getDriver().getId().equals(user.getId());
        if (!byDriver) {
            GarageAccessPolicy.assertCanManage(garage, user);
        }
        if (!reservation.getStatus().isCancellable()) {
            throw new ConflictException("Solo se pueden cancelar reservas pendientes de pago o confirmadas (estado actual: "
                    + reservation.getStatus() + ")");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(policy.now());
        reservation.setCancellationReason(reason == null || reason.isBlank() ? null : reason.strip());
        releaseSpot(reservation, garage);
        eventPublisher.publishEvent(new ReservationCancelledEvent(reservation.getId()));
        notifier.cancelled(reservation, byDriver);
        return toResponse(reservation);
    }

    // ---------- pago ----------

    /**
     * Lo llama el módulo de pagos cuando la pasarela aprueba el pago. Devuelve false si la reserva
     * ya no se puede confirmar (venció o se canceló antes de que llegara la aprobación).
     */
    @Transactional
    public boolean confirmPayment(UUID reservationId) {
        Locked locked = lock(reservationId);
        Reservation reservation = locked.reservation();
        Garage garage = locked.garage();
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            return reservation.getStatus() == ReservationStatus.CONFIRMED;
        }
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPaidAt(policy.now());
        if (!reservation.isSpotHeld() && policy.shouldHoldSpot(reservation.getStartTime())) {
            holdSpot(reservation, garage, reservation.getSpot());
        }
        notifier.confirmed(reservation);
        return true;
    }

    // ---------- entrada y salida (T5-02) ----------

    @Transactional
    public ReservationResponse checkIn(String rawCode, UserAccount user) {
        Locked locked = lock(idForCode(rawCode));
        Reservation reservation = locked.reservation();
        Garage garage = locked.garage();
        GarageAccessPolicy.assertCanManage(garage, user);
        Instant now = policy.now();

        if (reservation.getStatus() == ReservationStatus.PENDING_PAYMENT) {
            throw new ConflictException("La reserva todavía no está pagada");
        }
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new ConflictException("La reserva no admite entrada (estado actual: " + reservation.getStatus() + ")");
        }
        if (now.isBefore(reservation.getStartTime().minus(policy.getCheckInEarly()))) {
            throw new ConflictException("Todavía es temprano: la entrada se habilita "
                    + policy.getCheckInEarly().toMinutes() + " minutos antes del inicio de la reserva");
        }
        if (!now.isBefore(reservation.getEndTime())) {
            throw new ConflictException("La franja de la reserva ya terminó");
        }
        reservation.setStatus(ReservationStatus.CHECKED_IN);
        reservation.setCheckInAt(now);
        if (reservation.getSpot() != null) {
            parkingSpotService.applyReservationStatus(garage, reservation.getSpot(), SpotStatus.OCCUPIED);
        }
        reservation.setSpotHeld(false);
        notifier.checkedIn(reservation);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse checkOut(String rawCode, UserAccount user) {
        Locked locked = lock(idForCode(rawCode));
        Reservation reservation = locked.reservation();
        Garage garage = locked.garage();
        GarageAccessPolicy.assertCanManage(garage, user);
        if (reservation.getStatus() != ReservationStatus.CHECKED_IN) {
            throw new ConflictException("La reserva no tiene una entrada registrada (estado actual: " + reservation.getStatus() + ")");
        }
        Instant now = policy.now();
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setCheckOutAt(now);
        long overtime = Math.max(0, Duration.between(reservation.getEndTime(), now).toMinutes());
        reservation.setOvertimeMinutes((int) overtime);
        reservation.setOvertimeAmount(policy.priceForMinutes(reservation.getPricePerHour(), overtime));
        if (reservation.getSpot() != null) {
            parkingSpotService.applyReservationStatus(garage, reservation.getSpot(), SpotStatus.AVAILABLE);
        }
        notifier.checkedOut(reservation);
        return toResponse(reservation);
    }

    // ---------- tareas automáticas (ReservationJobs) ----------

    @Transactional(readOnly = true)
    public List<UUID> findExpiredUnpaidIds() {
        return reservationRepository.findByStatusAndPaymentDeadlineBefore(ReservationStatus.PENDING_PAYMENT, policy.now())
                .stream().map(Reservation::getId).toList();
    }

    /** Vence una reserva sin pagar y libera su plaza (T4-06). Idempotente. */
    @Transactional
    public boolean expire(UUID reservationId) {
        Locked locked = lock(reservationId);
        Reservation reservation = locked.reservation();
        Garage garage = locked.garage();
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT || reservation.getPaymentDeadline().isAfter(policy.now())) {
            return false;
        }
        reservation.setStatus(ReservationStatus.EXPIRED);
        releaseSpot(reservation, garage);
        notifier.expired(reservation);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UUID> findNoShowIds() {
        return reservationRepository.findByStatusAndEndTimeBefore(ReservationStatus.CONFIRMED, policy.now())
                .stream().map(Reservation::getId).toList();
    }

    @Transactional
    public boolean markNoShow(UUID reservationId) {
        Locked locked = lock(reservationId);
        Reservation reservation = locked.reservation();
        Garage garage = locked.garage();
        if (reservation.getStatus() != ReservationStatus.CONFIRMED || reservation.getEndTime().isAfter(policy.now())) {
            return false;
        }
        reservation.setStatus(ReservationStatus.NO_SHOW);
        releaseSpot(reservation, garage);
        notifier.noShow(reservation);
        return true;
    }

    @Transactional(readOnly = true)
    public List<UUID> findToHoldIds() {
        Instant now = policy.now();
        return reservationRepository.findToHold(
                        Set.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED),
                        now.plus(policy.getHoldBeforeStart()), now)
                .stream().map(Reservation::getId).toList();
    }

    /**
     * Aparta la plaza de una reserva que está por empezar. Si la plaza asignada está ocupada
     * (por ejemplo, un vehículo que se quedó más tiempo), se reasigna otra libre del mismo tipo.
     */
    @Transactional
    public boolean hold(UUID reservationId) {
        Locked locked = lock(reservationId);
        Reservation reservation = locked.reservation();
        Garage garage = locked.garage();
        if (reservation.isSpotHeld() || !reservation.getStatus().isCancellable()) {
            return false;
        }
        ParkingSpot spot = reservation.getSpot();
        if (spot == null || spot.getStatus() != SpotStatus.AVAILABLE) {
            Set<UUID> busy = new HashSet<>(reservationRepository.findBusySpotIds(garage.getId(),
                    reservation.getStartTime(), reservation.getEndTime(), ReservationStatus.ACTIVE));
            Optional<ParkingSpot> replacement = garage.getSpots().stream()
                    .filter(s -> s.getVehicleType() == reservation.getVehicleType())
                    .filter(s -> s.getStatus() == SpotStatus.AVAILABLE && !busy.contains(s.getId()))
                    .min(SPOT_ORDER);
            if (replacement.isEmpty()) {
                log.warn("No hay plaza libre para apartar la reserva {} en el garaje {}", reservation.getCode(), garage.getId());
                return false;
            }
            spot = replacement.get();
            reservation.setSpot(spot);
        }
        holdSpot(reservation, garage, spot);
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            notifier.reminder(reservation);
        }
        return true;
    }

    // ---------- helpers ----------

    private static final Comparator<ParkingSpot> SPOT_ORDER =
            Comparator.comparing(ParkingSpot::getFloor).thenComparing(ParkingSpot::getCode);

    /**
     * Plazas que pueden recibir la reserva: del tipo de vehículo pedido, no fuera de servicio y sin otra reserva
     * activa en la franja. Si la franja empieza ya (ventana de apartado), la plaza además debe estar libre ahora.
     */
    List<ParkingSpot> candidateSpots(Garage garage, VehicleType vehicleType, Instant start, Instant end) {
        Set<UUID> busy = new HashSet<>(reservationRepository.findBusySpotIds(garage.getId(), start, end, ReservationStatus.ACTIVE));
        boolean startsSoon = policy.shouldHoldSpot(start);
        return garage.getSpots().stream()
                .filter(spot -> spot.getVehicleType() == vehicleType)
                .filter(spot -> spot.getStatus() != SpotStatus.OUT_OF_SERVICE)
                .filter(spot -> !startsSoon || spot.getStatus() == SpotStatus.AVAILABLE)
                .filter(spot -> !busy.contains(spot.getId()))
                .sorted(SPOT_ORDER)
                .toList();
    }

    private static ParkingSpot pickSpot(List<ParkingSpot> candidates, UUID requestedSpotId) {
        if (requestedSpotId != null) {
            return candidates.stream()
                    .filter(spot -> spot.getId().equals(requestedSpotId))
                    .findFirst()
                    .orElseThrow(() -> new ConflictException("La plaza elegida no está disponible en esa franja"));
        }
        if (candidates.isEmpty()) {
            throw new ConflictException("No hay plazas libres para ese tipo de vehículo en esa franja");
        }
        return candidates.getFirst();
    }

    private void holdSpot(Reservation reservation, Garage garage, ParkingSpot spot) {
        if (spot == null) {
            return;
        }
        parkingSpotService.applyReservationStatus(garage, spot, SpotStatus.RESERVED);
        reservation.setSpotHeld(true);
    }

    private void releaseSpot(Reservation reservation, Garage garage) {
        ParkingSpot spot = reservation.getSpot();
        if (reservation.isSpotHeld() && spot != null && spot.getStatus() == SpotStatus.RESERVED) {
            parkingSpotService.applyReservationStatus(garage, spot, SpotStatus.AVAILABLE);
        }
        reservation.setSpotHeld(false);
    }

    private String newCode() {
        String code;
        do {
            code = CODE_PREFIX + SecureTokens.randomCode(CODE_LENGTH);
        } while (reservationRepository.existsByCode(code));
        return code;
    }

    private Integer searchSeconds(Instant searchStartedAt) {
        if (searchStartedAt == null) {
            return null;
        }
        long seconds = Duration.between(searchStartedAt, policy.now()).toSeconds();
        return seconds >= 0 && seconds <= MAX_SEARCH_SECONDS ? (int) seconds : null;
    }

    private static String normalizePlate(String plate) {
        return plate == null ? null : plate.strip().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    private Reservation findReservation(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada"));
    }

    private UUID idForCode(String rawCode) {
        String code = rawCode.strip().toUpperCase(Locale.ROOT);
        if (!code.startsWith(CODE_PREFIX)) {
            code = CODE_PREFIX + code;
        }
        return reservationRepository.findIdByCode(code)
                .orElseThrow(() -> new EntityNotFoundException("No existe una reserva con ese código"));
    }

    /**
     * Bloquea el garaje de la reserva y solo después carga la reserva: así su estado se lee
     * cuando ninguna otra operación la puede estar cambiando.
     */
    private Locked lock(UUID reservationId) {
        UUID garageId = reservationRepository.findGarageIdById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada"));
        Garage garage = garageRepository.findByIdForUpdate(garageId)
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
        return new Locked(findReservation(reservationId), garage);
    }

    private record Locked(Reservation reservation, Garage garage) {
    }

    /** El conductor de la reserva, el dueño del garaje o un administrador. */
    public static void assertCanView(Reservation reservation, UserAccount user) {
        boolean isDriver = reservation.getDriver().getId().equals(user.getId());
        boolean isOwner = reservation.getGarage().getOwner().getId().equals(user.getId());
        if (!isDriver && !isOwner && !user.isAdmin()) {
            throw new AccessDeniedException("No tienes permisos sobre esta reserva");
        }
    }

    public ReservationResponse toResponse(Reservation r) {
        Garage garage = r.getGarage();
        ParkingSpot spot = r.getSpot();
        UserAccount driver = r.getDriver();
        return new ReservationResponse(
                r.getId(),
                r.getCode(),
                r.getStatus(),
                garage.getId(),
                garage.getName(),
                garage.getAddressLine() + ", " + garage.getCity(),
                garage.getLatitude(),
                garage.getLongitude(),
                spot == null ? null : spot.getId(),
                spot == null ? null : spot.getCode(),
                spot == null ? null : spot.getFloor(),
                driver.getId(),
                displayName(driver),
                r.getVehicleType(),
                r.getVehiclePlate(),
                r.getStartTime(),
                r.getEndTime(),
                r.getDurationMinutes(),
                r.getPricePerHour(),
                r.getTotalAmount(),
                r.getOvertimeMinutes(),
                r.getOvertimeAmount(),
                CURRENCY,
                r.getPaymentDeadline(),
                r.getPaidAt() != null,
                r.getPaidAt(),
                r.getCheckInAt(),
                r.getCheckOutAt(),
                r.getCancelledAt(),
                r.getCancellationReason(),
                r.getStatus().isCancellable(),
                r.getCreatedAt()
        );
    }

    /** Nombre para mostrar: el nombre completo o, si no lo tiene, la parte del correo antes de la @. */
    public static String displayName(UserAccount user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        String email = user.getEmail();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }
}
