package com.biblioteca.chatbot.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Petición al asistente. El servidor no guarda estado: el cliente envía cada vez los mensajes
 * anteriores (solo texto) y el último debe ser la pregunta del usuario.
 *
 * @param messages mensajes en orden cronológico
 */
public record ChatRequest(
        @NotEmpty(message = "La conversación no puede estar vacía")
        @Size(max = 100, message = "La conversación es demasiado larga")
        List<@NotNull @Valid ChatMessage> messages) {
}
