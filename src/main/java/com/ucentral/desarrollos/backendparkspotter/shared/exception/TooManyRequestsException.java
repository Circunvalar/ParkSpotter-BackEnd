package com.ucentral.desarrollos.backendparkspotter.shared.exception;

import org.springframework.http.HttpStatus;

/** 429: demasiados intentos (por ejemplo, bloqueo temporal de login). */
public class TooManyRequestsException extends ApiException {
    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
