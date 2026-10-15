package com.biblioteca.chatbot.model;

/**
 * Respuesta del asistente.
 *
 * @param reply texto plano (sin Markdown) listo para mostrar
 */
public record ChatResponse(String reply) {
}
