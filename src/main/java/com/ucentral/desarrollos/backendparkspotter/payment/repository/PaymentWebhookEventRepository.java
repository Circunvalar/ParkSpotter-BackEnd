package com.ucentral.desarrollos.backendparkspotter.payment.repository;

import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    boolean existsByEventKey(String eventKey);
}
