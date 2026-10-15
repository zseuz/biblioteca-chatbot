package com.biblioteca.chatbot.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Un mensaje de la conversación.
 *
 * @param role    {@code user} (lo que escribe la persona) o {@code assistant} (respuestas anteriores)
 * @param content texto del mensaje
 */
public record ChatMessage(
        @NotNull @Pattern(regexp = "user|assistant", message = "role debe ser 'user' o 'assistant'") String role,
        @NotBlank(message = "El mensaje no puede estar vacío")
        @Size(max = 2000, message = "El mensaje no puede superar los 2000 caracteres") String content) {

    /** {@code true} si lo escribió la persona (no el asistente). */
    public boolean fromUser() {
        return "user".equals(role);
    }
}
