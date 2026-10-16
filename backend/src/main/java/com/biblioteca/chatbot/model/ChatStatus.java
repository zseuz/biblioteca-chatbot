package com.biblioteca.chatbot.model;

/**
 * Estado del asistente, para que la interfaz avise antes de que el usuario pregunte.
 *
 * @param bibliotecaAvailable la API de la biblioteca responde
 */
public record ChatStatus(boolean bibliotecaAvailable) {
}
