package com.biblioteca.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Usuario de la biblioteca ({@code GET /api/members}).
 *
 * @param activeLoans préstamos sin devolver (en plazo o vencidos)
 * @param totalLoans  préstamos registrados en total
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Member(Long id, String name, String email, long activeLoans, long totalLoans) {
}
