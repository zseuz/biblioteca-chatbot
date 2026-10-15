package com.biblioteca.chatbot.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Cuerpo de todas las respuestas de error (mismo formato que la API de la biblioteca).
 *
 * @param fields errores por campo; solo aparece en los 400 de validación
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String timestamp, int status, String message, String path, Map<String, String> fields) {
}
