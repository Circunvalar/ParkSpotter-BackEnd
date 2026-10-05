package com.ucentral.desarrollos.backendparkspotter.garageManagement.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageUpdateRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/garages")
@RequiredArgsConstructor
public class GarageController {

    private final GarageService garageService;

    @PostMapping
    public ResponseEntity<GarageResponse> create(@Valid @RequestBody GarageRequest request,
                                                  @AuthenticationPrincipal UserAccount owner) {
        return ResponseEntity.ok(garageService.create(request, owner));
    }

    @GetMapping
    public ResponseEntity<List<GarageResponse>> listActive(@RequestParam(required = false) String city) {
        if (city != null && !city.isBlank()) {
            return ResponseEntity.ok(garageService.listByCity(city));
        }
        return ResponseEntity.ok(garageService.listActive());
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<GarageResponse>> nearby(@RequestParam double lat,
                                                         @RequestParam double lng,
                                                         @RequestParam(defaultValue = "5") double radiusKm) {
        return ResponseEntity.ok(garageService.findNearby(lat, lng, radiusKm));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<GarageResponse>> mine(@AuthenticationPrincipal UserAccount owner) {
        return ResponseEntity.ok(garageService.listMine(owner));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GarageResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(garageService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GarageResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody GarageUpdateRequest request,
                                                  @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(garageService.update(id, request, user));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<GarageResponse> changeStatus(@PathVariable UUID id,
                                                        @RequestParam GarageStatus status,
                                                        @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(garageService.changeStatus(id, status, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal UserAccount user) {
        garageService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
