package com.ucentral.desarrollos.backendparkspotter.shared.exception;

import org.springframework.http.HttpStatus;

/** 401: credenciales o token inválidos. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
