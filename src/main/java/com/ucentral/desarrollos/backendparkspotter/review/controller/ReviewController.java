package com.ucentral.desarrollos.backendparkspotter.review.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.review.dto.ReviewRequest;
import com.ucentral.desarrollos.backendparkspotter.review.dto.ReviewResponse;
import com.ucentral.desarrollos.backendparkspotter.review.service.ReviewService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/api/v1/reservations/{reservationId}/review")
    public ResponseEntity<ReviewResponse> create(@PathVariable UUID reservationId,
                                                 @Valid @RequestBody ReviewRequest request,
                                                 @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(reservationId, request, user));
    }

    /** Público: reseñas del garaje, más recientes primero. */
    @GetMapping("/api/v1/garages/{garageId}/reviews")
    public ResponseEntity<PageResponse<ReviewResponse>> list(@PathVariable UUID garageId,
                                                             @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                             @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(reviewService.listForGarage(garageId, page, size));
    }

    @DeleteMapping("/api/v1/reviews/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable UUID reviewId, @AuthenticationPrincipal UserAccount user) {
        reviewService.delete(reviewId, user);
        return ResponseEntity.noContent().build();
    }
}
