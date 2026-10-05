package com.ucentral.desarrollos.backendparkspotter.shared.exception;

import org.springframework.http.HttpStatus;

/** 409: el recurso ya existe o choca con el estado actual. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
