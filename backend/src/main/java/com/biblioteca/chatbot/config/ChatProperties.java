package com.biblioteca.chatbot.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de la conexión con Claude.
 *
 * @param apiKey        clave de la API de Claude ({@code ANTHROPIC_API_KEY}); vacía = asistente desactivado
 * @param model         modelo de Claude que responde las preguntas
 * @param maxToolRounds consultas a la biblioteca que puede encadenar el modelo en una sola respuesta
 *                      (tope de seguridad para que una pregunta no genere un bucle sin fin)
 * @param maxHistory    mensajes de la conversación que se envían como contexto (los más recientes)
 */
@Validated
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(@DefaultValue("") String apiKey,
                             @DefaultValue("claude-opus-5-5") @NotBlank String model,
                             @DefaultValue("6") @Min(1) @Max(20) int maxToolRounds,
                             @DefaultValue("20") @Min(1) @Max(100) int maxHistory) {

    /** {@code true} si hay clave de API, es decir, si el asistente puede responder. */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
