package com.ucentral.desarrollos.backendparkspotter.report.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.report.dto.AdminReportResponse;
import com.ucentral.desarrollos.backendparkspotter.report.dto.OwnerReportResponse;
import com.ucentral.desarrollos.backendparkspotter.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Reportes. Sin fechas: últimos 30 días hasta hoy. Rango máximo 366 días. */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @GetMapping("/owner")
    public ResponseEntity<OwnerReportResponse> owner(@AuthenticationPrincipal UserAccount owner,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) UUID garageId) {
        return ResponseEntity.ok(reportService.ownerReport(owner, from, to, garageId));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<AdminReportResponse> admin(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.adminReport(from, to));
    }
}
