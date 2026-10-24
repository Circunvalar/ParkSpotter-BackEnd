package com.ucentral.desarrollos.backendparkspotter.reservation.repository;

import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID>, JpaSpecificationExecutor<Reservation> {

    boolean existsByCode(String code);

    @Query("SELECT r.id FROM Reservation r WHERE r.code = :code")
    Optional<UUID> findIdByCode(@Param("code") String code);

    @Query("SELECT r.garage.id FROM Reservation r WHERE r.id = :id")
    Optional<UUID> findGarageIdById(@Param("id") UUID id);

    /** Plazas del garaje con reservas activas que se cruzan con la franja [start, end). */
    @Query("""
            SELECT r.spot.id FROM Reservation r
            WHERE r.garage.id = :garageId AND r.spot IS NOT NULL
              AND r.status IN :statuses
              AND r.startTime < :end AND r.endTime > :start
            """)
    List<UUID> findBusySpotIds(@Param("garageId") UUID garageId, @Param("start") Instant start, @Param("end") Instant end,
                               @Param("statuses") Collection<ReservationStatus> statuses);

    /** El conductor ya tiene una reserva activa que se cruza con la franja. */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.driver.id = :driverId AND r.status IN :statuses
              AND r.startTime < :end AND r.endTime > :start
            """)
    boolean driverHasOverlap(@Param("driverId") UUID driverId, @Param("start") Instant start, @Param("end") Instant end,
                             @Param("statuses") Collection<ReservationStatus> statuses);

    Page<Reservation> findByDriverIdOrderByStartTimeDesc(UUID driverId, Pageable pageable);

    Page<Reservation> findByDriverIdAndStatusOrderByStartTimeDesc(UUID driverId, ReservationStatus status, Pageable pageable);

    List<Reservation> findByStatusAndPaymentDeadlineBefore(ReservationStatus status, Instant now);

    List<Reservation> findByStatusAndEndTimeBefore(ReservationStatus status, Instant now);

    /** Reservas que entran en la ventana previa al inicio y todavía no apartaron su plaza. */
    @Query("""
            SELECT r FROM Reservation r
            WHERE r.status IN :statuses AND r.spotHeld = false
              AND r.startTime <= :limit AND r.endTime > :now
            """)
    List<Reservation> findToHold(@Param("statuses") Collection<ReservationStatus> statuses,
                                 @Param("limit") Instant limit, @Param("now") Instant now);

    long countByDriverIdAndStatusIn(UUID driverId, Collection<ReservationStatus> statuses);

    @Query("SELECT COUNT(r) FROM Reservation r WHERE r.garage.owner.id = :ownerId AND r.status IN :statuses")
    long countByGarageOwnerAndStatusIn(@Param("ownerId") UUID ownerId, @Param("statuses") Collection<ReservationStatus> statuses);

    /** Reservas cuya franja empieza dentro del rango, de los garajes indicados (reportes). */
    @Query("""
            SELECT r FROM Reservation r JOIN FETCH r.garage g
            WHERE g.id IN :garageIds AND r.startTime >= :from AND r.startTime < :to
            """)
    List<Reservation> findForReport(@Param("garageIds") Collection<UUID> garageIds,
                                    @Param("from") Instant from, @Param("to") Instant to);

    @Query("SELECT r FROM Reservation r JOIN FETCH r.garage WHERE r.startTime >= :from AND r.startTime < :to")
    List<Reservation> findAllForReport(@Param("from") Instant from, @Param("to") Instant to);
}
