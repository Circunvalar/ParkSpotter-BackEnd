package com.ucentral.desarrollos.backendparkspotter.payment.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;
import com.ucentral.desarrollos.backendparkspotter.notification.service.NotificationService;
import com.ucentral.desarrollos.backendparkspotter.payment.dto.PaymentResponse;
import com.ucentral.desarrollos.backendparkspotter.payment.dto.ReceiptResponse;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.Payment;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentWebhookEvent;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.PaymentGateway;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.PaymentGateway.CheckoutSession;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.PaymentGateway.WebhookEvent;
import com.ucentral.desarrollos.backendparkspotter.payment.repository.PaymentRepository;
import com.ucentral.desarrollos.backendparkspotter.payment.repository.PaymentWebhookEventRepository;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationCancelledEvent;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.reservation.service.ReservationService;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Pagos de reservas (Sprint 05): crear el pago en la pasarela (T5-03), procesar el webhook con
 * verificación de firma e idempotencia (T5-04) y emitir el comprobante (T5-05).
 */
@Slf4j
@Service
public class PaymentService {

    static final String CURRENCY = "COP";

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final PaymentGateway gateway;
    private final NotificationService notificationService;
    private final Clock clock;
    private final List<String> allowedRedirectPrefixes;
    private final String defaultRedirectUrl;

    public PaymentService(PaymentRepository paymentRepository,
                          PaymentWebhookEventRepository webhookEventRepository,
                          ReservationRepository reservationRepository,
                          ReservationService reservationService,
                          PaymentGateway gateway,
                          NotificationService notificationService,
                          Clock clock,
                          @Value("${app.payments.allowed-redirect-prefixes:http://localhost:3000/,parkspotter://}") List<String> allowedRedirectPrefixes,
                          @Value("${app.payments.default-redirect-url:http://localhost:3000/pagos/resultado}") String defaultRedirectUrl) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.gateway = gateway;
        this.notificationService = notificationService;
        this.clock = clock;
        this.allowedRedirectPrefixes = allowedRedirectPrefixes.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
        this.defaultRedirectUrl = defaultRedirectUrl;
    }

    // ---------- crear pago ----------

    /**
     * Crea (o reutiliza) el pago pendiente de una reserva y devuelve los datos del checkout.
     * Llamarlo dos veces seguidas no crea dos cobros: se devuelve el mismo pago pendiente.
     */
    @Transactional
    public PaymentResponse create(UUID reservationId, String redirectUrl, UserAccount user) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada"));
        if (!reservation.getDriver().getId().equals(user.getId())) {
            throw new AccessDeniedException("Solo el conductor de la reserva puede pagarla");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new ConflictException("La reserva no está pendiente de pago (estado actual: " + reservation.getStatus() + ")");
        }
        if (!reservation.getPaymentDeadline().isAfter(Instant.now(clock))) {
            throw new ConflictException("El tiempo para pagar esta reserva ya terminó");
        }
        String redirect = resolveRedirect(redirectUrl);

        Optional<Payment> pending = paymentRepository.findFirstByReservationIdAndStatusOrderByCreatedAtDesc(reservationId, PaymentStatus.PENDING);
        if (pending.isPresent() && redirect.equals(pending.get().getRedirectUrl())) {
            Payment payment = pending.get();
            return toResponse(payment, gateway.createCheckout(payment, reservation.getDriver().getEmail()));
        }

        Payment payment = new Payment();
        payment.setReservation(reservation);
        payment.setReference(reservation.getCode() + "-" + (paymentRepository.countByReservationId(reservationId) + 1));
        payment.setAmount(reservation.getTotalAmount());
        payment.setAmountInCents(toCents(reservation.getTotalAmount()));
        payment.setCurrency(CURRENCY);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setProvider(gateway.provider());
        payment.setRedirectUrl(redirect);
        CheckoutSession checkout = gateway.createCheckout(payment, reservation.getDriver().getEmail());
        payment.setCheckoutUrl(checkout.checkoutUrl());
        Payment saved = paymentRepository.save(payment);
        return toResponse(saved, checkout);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(UUID paymentId, UserAccount user) {
        Payment payment = findPayment(paymentId);
        ReservationService.assertCanView(payment.getReservation(), user);
        return toResponse(payment, null);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForReservation(UUID reservationId, UserAccount user) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada"));
        ReservationService.assertCanView(reservation, user);
        return paymentRepository.findByReservationIdOrderByCreatedAtDesc(reservationId).stream()
                .map(payment -> toResponse(payment, null))
                .toList();
    }

    // ---------- webhook ----------

    /**
     * Procesa un webhook de la pasarela. Devuelve false si el evento ya se había procesado.
     *
     * @throws PaymentGateway.InvalidWebhookException si la firma no es válida (responder 401)
     */
    @Transactional
    public boolean handleWebhook(String rawBody, Map<String, String> headers) {
        WebhookEvent event = gateway.parseWebhook(rawBody, headers);
        return apply(event);
    }

    /** Resultado simulado de la pasarela sandbox: hace el mismo recorrido que un webhook real. */
    @Transactional
    public PaymentResponse simulateSandboxResult(UUID paymentId, PaymentStatus status, UserAccount user) {
        Payment payment = findPayment(paymentId);
        if (!"SANDBOX".equals(payment.getProvider())) {
            throw new ApiException("Solo los pagos de la pasarela simulada aceptan resultados simulados");
        }
        if (!payment.getReservation().getDriver().getId().equals(user.getId())) {
            throw new AccessDeniedException("Solo el conductor de la reserva puede simular su pago");
        }
        String transactionId = "sandbox-" + payment.getId();
        apply(new WebhookEvent("SANDBOX:" + transactionId + ":" + status, payment.getReference(), transactionId,
                status, payment.getAmountInCents(), "SANDBOX"));
        return toResponse(findPayment(paymentId), null);
    }

    private boolean apply(WebhookEvent event) {
        if (webhookEventRepository.existsByEventKey(event.eventKey())) {
            return false;
        }
        PaymentWebhookEvent record = new PaymentWebhookEvent();
        record.setEventKey(event.eventKey());
        record.setProvider(gateway.provider());
        record.setReference(event.reference());
        record.setStatus(event.status().name());
        webhookEventRepository.saveAndFlush(record);

        Payment payment = event.reference() == null ? null : paymentRepository.findByReference(event.reference()).orElse(null);
        if (payment == null) {
            log.warn("Webhook de pago con referencia desconocida: {}", event.reference());
            return true;
        }
        if (payment.getStatus() == PaymentStatus.APPROVED || payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            // Un pago aprobado no retrocede por un evento tardío o fuera de orden.
            return true;
        }
        payment.setProviderTransactionId(event.transactionId());
        if (event.paymentMethod() != null) {
            payment.setPaymentMethod(event.paymentMethod());
        }
        if (event.status() == PaymentStatus.APPROVED && event.amountInCents() != payment.getAmountInCents()) {
            log.error("Monto del webhook ({}) distinto al del pago {} ({})", event.amountInCents(), payment.getReference(), payment.getAmountInCents());
            payment.setStatus(PaymentStatus.ERROR);
            return true;
        }
        payment.setStatus(event.status());
        Reservation reservation = payment.getReservation();
        if (event.status() == PaymentStatus.APPROVED) {
            payment.setApprovedAt(Instant.now(clock));
            if (!reservationService.confirmPayment(reservation.getId())) {
                // La reserva venció o se canceló antes de que llegara la aprobación: hay que devolver el dinero.
                payment.setStatus(PaymentStatus.REFUND_PENDING);
                log.warn("Pago {} aprobado para una reserva que ya no está pendiente: requiere reembolso", payment.getReference());
            }
        } else if (event.status() == PaymentStatus.DECLINED || event.status() == PaymentStatus.ERROR) {
            notificationService.notify(reservation.getDriver(), NotificationType.PAYMENT_DECLINED, "Pago no aprobado",
                    "El pago de tu reserva " + reservation.getCode() + " no fue aprobado. Puedes intentarlo de nuevo antes de que venza.",
                    reservation.getId(), false);
        }
        return true;
    }

    /** Si se cancela una reserva ya pagada, su pago queda marcado para reembolso. */
    @EventListener
    public void onReservationCancelled(ReservationCancelledEvent event) {
        paymentRepository.findByReservationIdAndStatusIn(event.reservationId(), List.of(PaymentStatus.APPROVED))
                .forEach(payment -> payment.setStatus(PaymentStatus.REFUND_PENDING));
    }

    // ---------- comprobante ----------

    @Transactional(readOnly = true)
    public ReceiptResponse receipt(UUID reservationId, UserAccount user) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada"));
        ReservationService.assertCanView(reservation, user);
        Payment payment = paymentRepository.findByReservationIdAndStatusIn(reservationId,
                        List.of(PaymentStatus.APPROVED, PaymentStatus.REFUND_PENDING)).stream()
                .findFirst()
                .orElseThrow(() -> new ConflictException("La reserva no tiene un pago aprobado"));
        return new ReceiptResponse(
                "RC-" + payment.getReference(),
                Instant.now(clock),
                reservation.getId(),
                reservation.getCode(),
                reservation.getStatus().name(),
                ReservationService.displayName(reservation.getDriver()),
                reservation.getDriver().getEmail(),
                reservation.getGarage().getName(),
                reservation.getGarage().getAddressLine() + ", " + reservation.getGarage().getCity(),
                reservation.getSpot() == null ? null : reservation.getSpot().getCode(),
                reservation.getVehicleType(),
                reservation.getVehiclePlate(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                reservation.getDurationMinutes(),
                reservation.getPricePerHour(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getPaymentMethod(),
                payment.getReference(),
                payment.getProviderTransactionId(),
                payment.getApprovedAt(),
                payment.getStatus().name()
        );
    }

    // ---------- helpers ----------

    private String resolveRedirect(String requested) {
        if (requested == null || requested.isBlank()) {
            return defaultRedirectUrl;
        }
        String url = requested.trim();
        if (allowedRedirectPrefixes.stream().noneMatch(url::startsWith)) {
            throw new ApiException("redirectUrl no permitida: debe empezar por " + String.join(" o ", allowedRedirectPrefixes));
        }
        return url;
    }

    static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    private Payment findPayment(UUID id) {
        return paymentRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Pago no encontrado"));
    }

    private PaymentResponse toResponse(Payment payment, CheckoutSession checkout) {
        return new PaymentResponse(
                payment.getId(),
                payment.getReservation().getId(),
                payment.getReference(),
                payment.getAmount(),
                payment.getAmountInCents(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                payment.getPaymentMethod(),
                checkout != null ? checkout.checkoutUrl() : payment.getCheckoutUrl(),
                payment.getRedirectUrl(),
                checkout == null ? null : checkout.publicKey(),
                checkout == null ? null : checkout.integritySignature(),
                payment.getCreatedAt(),
                payment.getApprovedAt()
        );
    }
}
