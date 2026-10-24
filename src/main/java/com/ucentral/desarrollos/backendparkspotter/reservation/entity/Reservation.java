package com.ucentral.desarrollos.backendparkspotter.reservation.entity;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.ParkingSpot;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva de una plaza en una franja horaria (Sprint 04, PB-16).
 * Las horas se guardan como Instant (UTC); los fronts las muestran en la hora local.
 */
@Getter
@Setter
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservations_driver_start", columnList = "driver_id, start_time"),
        @Index(name = "idx_reservations_garage_status_time", columnList = "garage_id, status, start_time, end_time"),
        @Index(name = "idx_reservations_status_deadline", columnList = "status, payment_deadline"),
        @Index(name = "idx_reservations_spot", columnList = "spot_id")
})
public class Reservation {

    @Id
    @GeneratedValue
    private UUID id;

    /** Bloqueo optimista: dos cambios simultáneos sobre la misma reserva no se pisan. */
    @Version
    private Long version;

    /** Código que el conductor muestra en la entrada (ej. PS-7K3M9Q). */
    @Column(nullable = false, unique = true, length = 12)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserAccount driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "garage_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Garage garage;

    /** Plaza asignada. Si la plaza se elimina más adelante, el historial conserva la reserva sin plaza. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private ParkingSpot spot;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_plate", length = 10)
    private String vehiclePlate;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    /** Tarifa del garaje en el momento de reservar (si el dueño la cambia después, la reserva no cambia). */
    @Column(name = "price_per_hour", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerHour;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.PENDING_PAYMENT;

    /** Hasta cuándo se puede pagar; después la reserva vence sola. */
    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    /** true cuando la plaza ya quedó en estado RESERVED por esta reserva (se libera al cancelar o vencer). */
    @Column(name = "spot_held", nullable = false)
    private boolean spotHeld = false;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "check_in_at")
    private Instant checkInAt;

    @Column(name = "check_out_at")
    private Instant checkOutAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes = 0;

    @Column(name = "overtime_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    /** Segundos entre el inicio de la búsqueda en el front y la creación de la reserva (PB-27). */
    @Column(name = "search_to_reservation_seconds")
    private Integer searchToReservationSeconds;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public long getDurationMinutes() {
        return Duration.between(startTime, endTime).toMinutes();
    }
}
