package com.biblioteca.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Panel de estadísticas de la biblioteca ({@code GET /api/stats}).
 *
 * @param activeLoans préstamos en plazo (los vencidos van aparte en {@code overdueLoans})
 * @param topBooks    libros más prestados; {@code label} es el título
 * @param topMembers  usuarios con más préstamos; {@code label} es el nombre
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Stats(long totalBooks, long totalMembers, long activeLoans, long overdueLoans, long returnedLoans,
                    List<Entry> topBooks, List<Entry> loansByGenre, List<Entry> topMembers) {

    /** Un par «etiqueta → cantidad» de los rankings. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String label, long count) {
    }

    public List<Entry> topBooks() {
        return topBooks == null ? List.of() : topBooks;
    }

    public List<Entry> loansByGenre() {
        return loansByGenre == null ? List.of() : loansByGenre;
    }

    public List<Entry> topMembers() {
        return topMembers == null ? List.of() : topMembers;
    }
}
