package com.biblioteca.chatbot.model;

/**
 * Estado del asistente, para que la interfaz avise antes de que el usuario pregunte.
 *
 * @param claudeConfigured     hay clave de la API de Claude
 * @param bibliotecaAvailable  la API de la biblioteca responde
 */
public record ChatStatus(boolean claudeConfigured, boolean bibliotecaAvailable) {
}
