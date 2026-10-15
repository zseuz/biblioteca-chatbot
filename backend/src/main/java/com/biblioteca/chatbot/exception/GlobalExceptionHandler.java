package com.biblioteca.chatbot.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce las excepciones a respuestas JSON con el formato {@link ApiError}. Los errores
 * inesperados devuelven un mensaje genérico; el detalle solo queda en el log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Conversación mal formada → 400 con el motivo. */
    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    /** Falló la validación del cuerpo → 400 con el error de cada campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "Datos inválidos", req, fields);
    }

    /** El cuerpo no es JSON válido → 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return body(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es JSON válido", req, null);
    }

    /** Claude sin configurar o sin respuesta → 503 con el motivo. */
    @ExceptionHandler(ServiceUnavailableException.class)
    ResponseEntity<ApiError> unavailable(ServiceUnavailableException ex, HttpServletRequest req) {
        return body(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), req, null);
    }

    /** Ruta inexistente → 404 con el mismo formato JSON. */
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noRoute(NoResourceFoundException ex, HttpServletRequest req) {
        return body(HttpStatus.NOT_FOUND, "Recurso no encontrado", req, null);
    }

    /** Red de seguridad: cualquier error no previsto. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req, null);
    }

    private ResponseEntity<ApiError> body(HttpStatus status, String message,
                                          HttpServletRequest req, Map<String, String> fields) {
        ApiError error = new ApiError(Instant.now().toString(), status.value(), message, req.getRequestURI(), fields);
        return ResponseEntity.status(status).body(error);
    }
}
