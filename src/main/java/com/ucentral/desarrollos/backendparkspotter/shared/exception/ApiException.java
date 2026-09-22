package com.ucentral.desarrollos.backendparkspotter.shared.exception;

public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}
