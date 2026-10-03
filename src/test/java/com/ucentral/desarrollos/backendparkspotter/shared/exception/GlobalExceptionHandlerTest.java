package com.ucentral.desarrollos.backendparkspotter.shared.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Casos del contrato de errores difíciles de provocar por HTTP (conflictos de BD, headers
 * obligatorios, errores inesperados): se prueba el manejador directamente.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/prueba");

    @Test
    void constraintViolation_Returns400WithDetailPerProperty() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ConstraintViolationException ex = new ConstraintViolationException(validator.validate(new Sample("")));

        ResponseEntity<ApiError> response = handler.handleConstraint(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details()).containsKey("name");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/prueba");
    }

    @Test
    void globalValidationErrors_AreIncludedInDetails() throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Sample("x"), "garage");
        result.reject("schedule", "horario inválido");
        MethodParameter parameter = new MethodParameter(Sample.class.getMethod("name"), -1);

        ResponseEntity<ApiError> response = handler.handleValidation(new MethodArgumentNotValidException(parameter, result), request);

        assertThat(response.getBody().details()).containsEntry("garage", "horario inválido");
    }

    @Test
    void missingHeader_Returns400() throws Exception {
        MethodParameter parameter = new MethodParameter(Sample.class.getMethod("echo", String.class), 0);

        ResponseEntity<ApiError> response = handler.handleMissingHeader(new MissingRequestHeaderException("X-Client-Id", parameter), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).contains("X-Client-Id");
    }

    @Test
    void databaseConflicts_Return409WithRetryMessage() {
        ResponseEntity<ApiError> lock = handler.handleConflict(new CannotAcquireLockException("lock"), request);
        ResponseEntity<ApiError> duplicate = handler.handleConflict(new DataIntegrityViolationException("uk"), request);

        assertThat(lock.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().message()).contains("intenta de nuevo");
    }

    @Test
    void accessDeniedWithoutMessage_UsesDefaultSpanishMessage() {
        ResponseEntity<ApiError> response = handler.handleAccessDenied(new AccessDeniedException(null), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("No tienes permisos para esta operación");
    }

    @Test
    void unreadableBodyWithoutJacksonCause_HasNoFieldDetails() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("vacío", new MockHttpInputMessage(new byte[0]));

        ResponseEntity<ApiError> response = handler.handleUnreadableBody(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details()).isEmpty();
    }

    @Test
    void unexpectedError_Returns500WithoutInternalDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new IllegalStateException("password=secreta en la BD"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Error interno del servidor");
        assertThat(response.getBody().toString()).doesNotContain("secreta");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void springMvcErrors_KeepTheirStatusWithSpanishMessage() {
        ResponseEntity<ApiError> notAcceptable = handler.handleUnexpected(
                new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON)), request);
        ResponseEntity<ApiError> timeout = handler.handleUnexpected(new AsyncRequestTimeoutException(), request);

        assertThat(notAcceptable.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(notAcceptable.getBody().message()).isEqualTo("Formato de respuesta no soportado");
        assertThat(timeout.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(timeout.getBody().message()).isEqualTo("Servicio no disponible temporalmente");
    }

    @Test
    void otherSpringErrors_UseTheStandardReasonPhrase() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.I_AM_A_TEAPOT);
        assertThat(response.getBody().message()).isEqualTo("I'm a teapot");
    }

    @Test
    void methodValidation_CombinesBodyFieldErrors_GlobalErrors_AndParameterErrors() throws Exception {
        Method method = Sample.class.getMethod("echo", String.class);
        MethodParameter bodyParameter = new MethodParameter(method, 0);
        // Parámetro sin nombre resoluble (clases compiladas sin -parameters)
        MethodParameter unnamedParameter = new MethodParameter(method, 0) {
            @Override
            public String getParameterName() {
                return null;
            }
        };

        BeanPropertyBindingResult bodyErrors = new BeanPropertyBindingResult(new Sample(""), "sample");
        bodyErrors.rejectValue("name", "NotBlank", "no debe estar vacío");
        bodyErrors.reject("cross", "regla entre campos");
        ParameterErrors body = new ParameterErrors(bodyParameter, new Sample(""), bodyErrors, null, null, null);
        ParameterValidationResult header = new ParameterValidationResult(unnamedParameter, "x".repeat(70),
                List.of(new DefaultMessageSourceResolvable(new String[]{"Size"}, "el tamaño debe estar entre 1 y 64")),
                null, null, null, (error, type) -> null);

        HandlerMethodValidationException ex = new HandlerMethodValidationException(
                MethodValidationResult.create(new Sample("x"), method, List.of(body, header)));

        ResponseEntity<ApiError> response = handler.handleMethodValidation(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().details())
                .containsEntry("name", "no debe estar vacío")
                .containsEntry("sample", "regla entre campos")
                .containsEntry("parameter", "el tamaño debe estar entre 1 y 64");
    }

    @Test
    void clientDisconnected_IsIgnoredSilently() {
        assertThatCode(() -> handler.handleClientGone(new AsyncRequestNotUsableException("cliente cerró")))
                .doesNotThrowAnyException();
    }

    @Test
    void apiExceptions_UseTheirOwnStatus() {
        assertThat(handler.handleApi(new ConflictException("duplicado"), request).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleApi(new UnauthorizedException("no"), request).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleApi(new TooManyRequestsException("espera"), request).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(handler.handleApi(new ApiException("malo"), request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /** DTO mínimo para generar violaciones reales y parámetros de método. */
    public record Sample(@NotBlank String name) {
        public String echo(String value) {
            return value;
        }
    }
}
