package com.biblioteca.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Libro tal como lo devuelve la API de la biblioteca ({@code GET /api/books}).
 *
 * @param availableCopies ejemplares que no están prestados ahora mismo
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Book(Long id, String title, String author, String genre, int totalCopies, int availableCopies) {

    /** {@code true} si queda al menos un ejemplar para prestar. */
    public boolean available() {
        return availableCopies > 0;
    }
}
