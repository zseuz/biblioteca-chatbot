package com.biblioteca.chatbot.client;

/**
 * La API de la biblioteca rechazó una consulta o no respondió. El mensaje se le pasa a Claude
 * como resultado de error de la herramienta, para que lo explique al usuario.
 */
public class BibliotecaApiException extends RuntimeException {

    /** @param message qué falló, en español. */
    public BibliotecaApiException(String message) {
        super(message);
    }
}
