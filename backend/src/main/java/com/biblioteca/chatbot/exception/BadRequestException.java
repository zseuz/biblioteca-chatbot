package com.biblioteca.chatbot.exception;

/** Petición con datos no válidos (p. ej. una conversación que no termina en el usuario) → 400. */
public class BadRequestException extends RuntimeException {

    /** @param message motivo, en español; el usuario lo lee tal cual. */
    public BadRequestException(String message) {
        super(message);
    }
}
