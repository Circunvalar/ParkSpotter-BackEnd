package com.ucentral.desarrollos.backendparkspotter.garageManagement.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Plazas de un garaje. El listado es público (los fronts dibujan el parqueadero completo);
 * crear, editar, eliminar y cambiar estado es solo para el dueño o un administrador.
 */
@RestController
@RequestMapping("/api/v1/garages/{garageId}/spots")
@RequiredArgsConstructor
public class ParkingSpotController {

    private final ParkingSpotService parkingSpotService;

    @GetMapping
    public ResponseEntity<List<ParkingSpotResponse>> list(@PathVariable UUID garageId,
                                                          @RequestParam(required = false) SpotStatus status,
                                                          @RequestParam(required = false) VehicleType vehicleType) {
        return ResponseEntity.ok(parkingSpotService.list(garageId, status, vehicleType));
    }

    @PostMapping
    public ResponseEntity<ParkingSpotResponse> add(@PathVariable UUID garageId,
                                                   @Valid @RequestBody ParkingSpotRequest request,
                                                   @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parkingSpotService.add(garageId, request, user));
    }

    @PutMapping("/{spotId}")
    public ResponseEntity<ParkingSpotResponse> update(@PathVariable UUID garageId,
                                                      @PathVariable UUID spotId,
                                                      @Valid @RequestBody ParkingSpotRequest request,
                                                      @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(parkingSpotService.update(garageId, spotId, request, user));
    }

    @PatchMapping("/{spotId}/status")
    public ResponseEntity<ParkingSpotResponse> changeStatus(@PathVariable UUID garageId,
                                                            @PathVariable UUID spotId,
                                                            @RequestParam SpotStatus status,
                                                            @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(parkingSpotService.changeStatus(garageId, spotId, status, user));
    }

    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> delete(@PathVariable UUID garageId,
                                       @PathVariable UUID spotId,
                                       @AuthenticationPrincipal UserAccount user) {
        parkingSpotService.delete(garageId, spotId, user);
        return ResponseEntity.noContent().build();
    }
}
