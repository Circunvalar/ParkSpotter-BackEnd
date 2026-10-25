package com.ucentral.desarrollos.backendparkspotter.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio con su código HTTP. Por defecto 400 (petición inválida);
 * las subclases cubren los demás casos (401, 409, 429...).
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
