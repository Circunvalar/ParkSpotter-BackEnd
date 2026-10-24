package com.ucentral.desarrollos.backendparkspotter.review.repository;

import com.ucentral.desarrollos.backendparkspotter.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByReservationId(UUID reservationId);

    Optional<Review> findByReservationId(UUID reservationId);

    Page<Review> findByGarageIdOrderByCreatedAtDesc(UUID garageId, Pageable pageable);

    /** [promedio, cantidad] de las calificaciones del garaje. */
    @Query("SELECT COALESCE(AVG(r.rating), 0), COUNT(r) FROM Review r WHERE r.garage.id = :garageId")
    Object[] ratingStats(@Param("garageId") UUID garageId);
}
