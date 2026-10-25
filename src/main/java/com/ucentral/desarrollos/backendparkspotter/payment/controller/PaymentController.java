package com.ucentral.desarrollos.backendparkspotter.payment.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.payment.dto.CreatePaymentRequest;
import com.ucentral.desarrollos.backendparkspotter.payment.dto.PaymentResponse;
import com.ucentral.desarrollos.backendparkspotter.payment.dto.ReceiptResponse;
import com.ucentral.desarrollos.backendparkspotter.payment.dto.SandboxResultRequest;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.PaymentGateway;
import com.ucentral.desarrollos.backendparkspotter.payment.service.PaymentService;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pagos de reservas, webhook de la pasarela y comprobante. */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/api/v1/reservations/{reservationId}/payments")
    public ResponseEntity<PaymentResponse> create(@PathVariable UUID reservationId,
                                                  @Valid @RequestBody(required = false) CreatePaymentRequest request,
                                                  @AuthenticationPrincipal UserAccount user) {
        String redirectUrl = request == null ? null : request.redirectUrl();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(reservationId, redirectUrl, user));
    }

    @GetMapping("/api/v1/reservations/{reservationId}/payments")
    public ResponseEntity<List<PaymentResponse>> listForReservation(@PathVariable UUID reservationId,
                                                                    @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(paymentService.listForReservation(reservationId, user));
    }

    @GetMapping("/api/v1/reservations/{reservationId}/receipt")
    public ResponseEntity<ReceiptResponse> receipt(@PathVariable UUID reservationId, @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(paymentService.receipt(reservationId, user));
    }

    @GetMapping("/api/v1/payments/{paymentId}")
    public ResponseEntity<PaymentResponse> get(@PathVariable UUID paymentId, @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(paymentService.get(paymentId, user));
    }

    /** Solo pasarela simulada (app.payments.provider=sandbox): aprobar o rechazar el pago para probar el flujo. */
    @PostMapping("/api/v1/payments/{paymentId}/sandbox-result")
    public ResponseEntity<PaymentResponse> sandboxResult(@PathVariable UUID paymentId,
                                                         @Valid @RequestBody SandboxResultRequest request,
                                                         @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(paymentService.simulateSandboxResult(paymentId, request.status(), user));
    }

    /**
     * Webhook de Wompi (configurar en el panel de Wompi como URL de eventos). Público: la autenticidad
     * se comprueba con la firma del evento. Responde 200 también a eventos repetidos para que Wompi no reintente.
     */
    @PostMapping("/api/v1/payments/webhooks/wompi")
    public ResponseEntity<Map<String, Object>> wompiWebhook(@RequestBody String rawBody, HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name.toLowerCase(), request.getHeader(name)));
        try {
            boolean processed = paymentService.handleWebhook(rawBody, headers);
            return ResponseEntity.ok(Map.of("received", true, "duplicate", !processed));
        } catch (PaymentGateway.InvalidWebhookException ex) {
            throw new UnauthorizedException(ex.getMessage());
        }
    }
}
