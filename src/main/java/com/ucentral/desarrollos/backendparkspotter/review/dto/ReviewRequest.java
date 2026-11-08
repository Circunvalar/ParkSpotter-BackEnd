package com.ucentral.desarrollos.backendparkspotter.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param rating         de 1 a 5 estrellas
 * @param securityRating opcional: qué tan seguro se sintió el lugar, de 1 a 5
 */
public record ReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @Size(max = 500) String comment,
        @Min(1) @Max(5) Integer securityRating
) {

    public ReviewRequest(Integer rating, String comment) {
        this(rating, comment, null);
    }
}
