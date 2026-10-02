package com.ucentral.desarrollos.backendparkspotter.shared.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Contrato único de errores para web y Android: siempre JSON con la forma de ApiError,
 * con el código HTTP correcto y, en errores de validación, el detalle por campo en "details".
 * Nunca se expone el stack trace ni mensajes internos.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String VALIDATION_FAILED = "Datos inválidos";

    // ---------- 400: validación y formato ----------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error -> details.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, request, details);
    }

    /** Validación de @RequestParam, @PathVariable, headers y @ModelAttribute (Spring 6.1+). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors errors) {
                for (FieldError error : errors.getFieldErrors()) {
                    details.putIfAbsent(error.getField(), error.getDefaultMessage());
                }
                errors.getGlobalErrors().forEach(error -> details.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
            } else {
                String name = result.getMethodParameter().getParameterName();
                String message = result.getResolvableErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("; "));
                details.putIfAbsent(name == null ? "parameter" : name, message);
            }
        });
        return build(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                details.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        return build(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, request, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Valor inválido para el parámetro '" + ex.getName() + "'";
        return build(HttpStatus.BAD_REQUEST, message, request, Map.of(ex.getName(), "valor no permitido: " + ex.getValue()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex.getCause() instanceof JacksonException jackson) {
            String field = jackson.getPath().stream()
                    .map(JacksonException.Reference::getPropertyName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));
            if (!field.isBlank()) {
                details.put(field, "valor con formato o tipo inválido");
            }
        }
        return build(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es un JSON válido", request, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + ex.getParameterName() + "'", request,
                Map.of(ex.getParameterName(), "es obligatorio"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Falta el header '" + ex.getHeaderName() + "'", request, Map.of());
    }

    // ---------- negocio, recursos y permisos ----------

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String message = ex.getMessage() == null ? "No tienes permisos para esta operación" : ex.getMessage();
        return build(HttpStatus.FORBIDDEN, message, request, Map.of());
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiError> handleConflict(RuntimeException ex, HttpServletRequest request) {
        log.warn("Conflicto de datos en {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.CONFLICT, "El recurso fue modificado por otra operación, intenta de nuevo", request, Map.of());
    }

    // ---------- infraestructura ----------

    /** El cliente cerró la conexión (típico en SSE): no hay a quién responder. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(AsyncRequestNotUsableException ex) {
        log.debug("Cliente desconectado: {}", ex.getMessage());
    }

    /**
     * Resto de errores. Las excepciones propias de Spring MVC (404 ruta inexistente, 405, 406, 415...)
     * conservan su código; cualquier otra cosa es un 500 sin detalles internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            return build(HttpStatus.valueOf(status.value()), messageFor(status), request, Map.of());
        }
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request, Map.of());
    }

    private static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 404 -> "Recurso no encontrado";
            case 405 -> "Método HTTP no permitido para esta ruta";
            case 406 -> "Formato de respuesta no soportado";
            case 415 -> "Content-Type no soportado: usa application/json";
            case 503 -> "Servicio no disponible temporalmente";
            default -> HttpStatus.valueOf(status.value()).getReasonPhrase();
        };
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request, Map<String, Object> details) {
        // Content-Type explícito: el error llega como JSON aunque el cliente pidiera otro formato (ej. SSE).
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(
                new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), details));
    }
}
