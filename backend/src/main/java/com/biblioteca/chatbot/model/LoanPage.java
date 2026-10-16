package com.biblioteca.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Una página del historial de préstamos ({@code GET /api/loans}).
 *
 * @param totalElements préstamos que cumplen el filtro en total (no solo los de esta página)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoanPage(List<Loan> content, long totalElements) {

    /** Nunca devuelve {@code null}, aunque la API no envíe el contenido. */
    public List<Loan> content() {
        return content == null ? List.of() : content;
    }
}
