package com.ucentral.desarrollos.backendparkspotter.review.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;
import com.ucentral.desarrollos.backendparkspotter.notification.service.NotificationService;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.review.dto.ReviewRequest;
import com.ucentral.desarrollos.backendparkspotter.review.dto.ReviewResponse;
import com.ucentral.desarrollos.backendparkspotter.review.entity.Review;
import com.ucentral.desarrollos.backendparkspotter.review.repository.ReviewRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/** Calificaciones de garajes (Sprint 06, T6-01): crear, listar, borrar y mantener el promedio del garaje. */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationRepository reservationRepository;
    private final GarageRepository garageRepository;
    private final NotificationService notificationService;

    @Transactional
    public ReviewResponse create(UUID reservationId, ReviewRequest request, UserAccount user) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reserva no encontrada"));
        if (!reservation.getDriver().getId().equals(user.getId())) {
            throw new AccessDeniedException("Solo el conductor de la reserva puede calificarla");
        }
        if (reservation.getStatus() != ReservationStatus.COMPLETED) {
            throw new ConflictException("Solo se pueden calificar reservas completadas");
        }
        if (reviewRepository.existsByReservationId(reservationId)) {
            throw new ConflictException("Esta reserva ya fue calificada");
        }
        // Bloqueo del garaje: dos calificaciones simultáneas no deben pisar el promedio.
        Garage garage = garageRepository.findByIdForUpdate(reservation.getGarage().getId())
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
        Review review = new Review();
        review.setReservation(reservation);
        review.setGarage(garage);
        review.setAuthor(user);
        review.setRating(request.rating());
        review.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().strip());
        Review saved = reviewRepository.saveAndFlush(review);
        refreshGarageRating(garage);

        notificationService.notify(garage.getOwner(), NotificationType.NEW_REVIEW, "Nueva calificación",
                garage.getName() + " recibió " + request.rating() + " de 5 estrellas.", garage.getId(), false);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listForGarage(UUID garageId, int page, int size) {
        if (!garageRepository.existsById(garageId)) {
            throw new EntityNotFoundException("Garage not found");
        }
        return PageResponse.of(reviewRepository.findByGarageIdOrderByCreatedAtDesc(garageId, PageRequest.of(page, size)),
                ReviewService::toResponse);
    }

    /** El autor o un administrador (moderación) pueden borrar una calificación. */
    @Transactional
    public void delete(UUID reviewId, UserAccount user) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new EntityNotFoundException("Calificación no encontrada"));
        if (!review.getAuthor().getId().equals(user.getId()) && !user.isAdmin()) {
            throw new AccessDeniedException("No puedes borrar esta calificación");
        }
        Garage garage = garageRepository.findByIdForUpdate(review.getGarage().getId())
                .orElseThrow(() -> new EntityNotFoundException("Garage not found"));
        reviewRepository.delete(review);
        reviewRepository.flush();
        refreshGarageRating(garage);
    }

    private void refreshGarageRating(Garage garage) {
        Object[] stats = unwrap(reviewRepository.ratingStats(garage.getId()));
        double average = ((Number) stats[0]).doubleValue();
        int count = ((Number) stats[1]).intValue();
        garage.setAverageRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP).doubleValue());
        garage.setRatingCount(count);
        garageRepository.save(garage);
    }

    /** Según el proveedor JPA, una consulta con varias columnas puede venir envuelta en otro arreglo. */
    private static Object[] unwrap(Object[] row) {
        return row.length == 1 && row[0] instanceof Object[] inner ? inner : row;
    }

    private static ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getGarage().getId(),
                review.getReservation().getId(),
                review.getRating(),
                review.getComment(),
                firstName(review.getAuthor()),
                review.getCreatedAt()
        );
    }

    static String firstName(UserAccount author) {
        if (author.getDeletedAt() != null || author.getFullName() == null || author.getFullName().isBlank()) {
            return "Usuario";
        }
        return author.getFullName().strip().split("\\s+")[0];
    }
}
