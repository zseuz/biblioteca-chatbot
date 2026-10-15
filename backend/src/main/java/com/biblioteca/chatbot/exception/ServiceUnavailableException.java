package com.biblioteca.chatbot.exception;

/**
 * Claude no está configurado o no responde → 503. El mensaje explica al usuario qué pasa
 * sin revelar detalles internos.
 */
public class ServiceUnavailableException extends RuntimeException {

    /** @param message explicación para el usuario final, en español. */
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
