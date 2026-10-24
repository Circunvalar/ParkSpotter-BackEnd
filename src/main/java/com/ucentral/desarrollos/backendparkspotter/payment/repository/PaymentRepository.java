package com.ucentral.desarrollos.backendparkspotter.payment.repository;

import com.ucentral.desarrollos.backendparkspotter.payment.entity.Payment;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByReference(String reference);

    List<Payment> findByReservationIdOrderByCreatedAtDesc(UUID reservationId);

    Optional<Payment> findFirstByReservationIdAndStatusOrderByCreatedAtDesc(UUID reservationId, PaymentStatus status);

    List<Payment> findByReservationIdAndStatusIn(UUID reservationId, Collection<PaymentStatus> statuses);

    long countByReservationId(UUID reservationId);
}
