package com.biblioteca.chatbot.model;

/**
 * Una pregunta ya interpretada.
 *
 * @param intent        qué se pregunta
 * @param subject       de quién o de qué (nombre de usuario, título, autor o género); vacío si no aplica
 * @param onlyAvailable la pregunta pide solo libros con ejemplares disponibles
 * @param activeOnly    la pregunta se refiere a préstamos actuales (p. ej. «más préstamos activos»)
 */
public record Question(Intent intent, String subject, boolean onlyAvailable, boolean activeOnly) {

    /** Pregunta sin sujeto ni matices. */
    public static Question of(Intent intent) {
        return new Question(intent, "", false, false);
    }

    /** {@code true} si la pregunta nombra a alguien o algo concreto. */
    public boolean hasSubject() {
        return !subject.isEmpty();
    }
}
