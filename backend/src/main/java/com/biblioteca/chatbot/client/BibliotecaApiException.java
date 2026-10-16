package com.biblioteca.chatbot.client;

/**
 * La API de la biblioteca rechazó una consulta o no respondió. El controlador la convierte en
 * un 503 cuyo mensaje se muestra en el chat con la opción de reintentar.
 */
public class BibliotecaApiException extends RuntimeException {

    /** @param message qué falló, en español. */
    public BibliotecaApiException(String message) {
        super(message);
    }
}
