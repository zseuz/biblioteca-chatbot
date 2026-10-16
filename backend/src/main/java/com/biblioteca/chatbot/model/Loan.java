package com.biblioteca.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * Préstamo tal como lo devuelve la API de la biblioteca ({@code GET /api/loans}).
 *
 * @param status {@code ACTIVE} (en plazo), {@code OVERDUE} (vencido) o {@code RETURNED} (devuelto)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Loan(Long id, String bookTitle, String memberName, LocalDate loanDate, LocalDate dueDate,
                   LocalDate returnDate, String status) {

    /** {@code true} si todavía no se ha devuelto (en plazo o vencido). */
    public boolean open() {
        return !"RETURNED".equals(status);
    }

    /** {@code true} si no se ha devuelto y ya pasó la fecha límite. */
    public boolean overdue() {
        return "OVERDUE".equals(status);
    }
}
