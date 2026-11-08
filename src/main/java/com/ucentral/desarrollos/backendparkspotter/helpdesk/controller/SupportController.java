package com.ucentral.desarrollos.backendparkspotter.helpdesk.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.ContactResponse;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.CreateTicketRequest;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.FaqResponse;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.TicketMessageRequest;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.TicketResponse;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.dto.TicketStatusRequest;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketCategory;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketStatus;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.service.SupportService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Soporte (RNF-10): preguntas frecuentes y contacto (públicos), casos del usuario y bandeja de soporte. */
@RestController
@RequiredArgsConstructor
public class SupportController {

    private final SupportService supportService;

    @GetMapping("/api/v1/support/faqs")
    public ResponseEntity<List<FaqResponse>> faqs(@RequestParam(required = false) TicketCategory category) {
        return ResponseEntity.ok(supportService.faqs(category));
    }

    @GetMapping("/api/v1/support/contact")
    public ResponseEntity<ContactResponse> contact() {
        return ResponseEntity.ok(supportService.contact());
    }

    @PostMapping("/api/v1/support/tickets")
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody CreateTicketRequest request,
                                                 @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supportService.create(request, user));
    }

    @GetMapping("/api/v1/support/tickets/mine")
    public ResponseEntity<PageResponse<TicketResponse>> mine(@AuthenticationPrincipal UserAccount user,
                                                             @RequestParam(required = false) TicketStatus status,
                                                             @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                             @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(supportService.mine(user, status, page, size));
    }

    @GetMapping("/api/v1/support/tickets/{id}")
    public ResponseEntity<TicketResponse> get(@PathVariable UUID id, @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(supportService.get(id, user));
    }

    @PostMapping("/api/v1/support/tickets/{id}/messages")
    public ResponseEntity<TicketResponse> reply(@PathVariable UUID id, @Valid @RequestBody TicketMessageRequest request,
                                                @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(supportService.reply(id, request, user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/admin/support/tickets")
    public ResponseEntity<PageResponse<TicketResponse>> inbox(@RequestParam(required = false) TicketStatus status,
                                                              @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(supportService.inbox(status, page, size));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/api/v1/admin/support/tickets/{id}/status")
    public ResponseEntity<TicketResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody TicketStatusRequest request) {
        return ResponseEntity.ok(supportService.changeStatus(id, request.status()));
    }
}
