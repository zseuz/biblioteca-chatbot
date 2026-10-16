package com.biblioteca.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.biblioteca.chatbot.model.Intent;
import com.biblioteca.chatbot.model.Question;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Cómo se interpreta cada pregunta: intención y sujeto. Cada fila es una pregunta tal como la
 * escribiría una persona (con tildes, mayúsculas y signos) y lo que se espera entender.
 */
class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @ParameterizedTest(name = "«{0}» → {1} «{2}»")
    @CsvSource(delimiter = '|', textBlock = """
            Hola                                              | GREETING      |
            Buenas tardes                                     | GREETING      |
            Hola, ¿qué préstamos están vencidos?              | OVERDUE_LOANS |
            Muchas gracias                                    | THANKS        |
            ayuda                                             | HELP          |
            ¿Qué puedes hacer?                                | HELP          |
            ¿Cuánto dura un préstamo?                         | RULES         |
            ¿Cuándo se puede renovar un préstamo?             | RULES         |
            ¿Cuántos libros puede pedir un usuario?           | RULES         |
            Presta Dune a Ana                                 | ACTION        |
            Quiero devolver un libro                          | ACTION        |
            ¿Qué préstamos están vencidos?                    | OVERDUE_LOANS |
            Préstamos atrasados de Ana Torres                 | OVERDUE_LOANS | Ana Torres
            ¿Quién tiene más préstamos activos?               | TOP_MEMBERS   |
            Usuarios que más libros piden                     | TOP_MEMBERS   |
            ¿Cuáles son los libros más prestados?             | TOP_BOOKS     |
            Géneros más populares                             | GENRES        |
            Estadísticas                                      | STATS         |
            ¿Cuántos libros hay?                              | STATS         |
            Préstamos en plazo                                | ACTIVE_LOANS  |
            ¿Qué libros hay disponibles?                      | LIST_BOOKS    |
            Muéstrame el catálogo                             | LIST_BOOKS    |
            Lista de usuarios                                 | LIST_MEMBERS  |
            ¿Qué libros tiene prestados Ana?                  | SUBJECT_LOANS | Ana
            ¿Cuántos préstamos tiene Ana Torres?              | SUBJECT_LOANS | Ana Torres
            ¿Quién tiene Cien años de soledad?                | SUBJECT_LOANS | Cien años de soledad
            ¿Hay ejemplares de Dune?                          | BOOK_SEARCH   | Dune
            ¿Qué libros de ciencia ficción hay disponibles?   | BOOK_SEARCH   | ciencia ficción
            Libros de García Márquez                          | BOOK_SEARCH   | García Márquez
            ¿Está disponible 1984?                            | BOOK_SEARCH   | 1984
            asdfgh qwerty                                     | BOOK_SEARCH   | asdfgh qwerty
            ¿?                                                | HELP          |
            """)
    void classifiesQuestions(String text, Intent intent, String subject) {
        Question q = classifier.classify(text);
        assertThat(q.intent()).isEqualTo(intent);
        assertThat(q.subject()).isEqualTo(subject == null ? "" : subject);
    }

    @ParameterizedTest(name = "«{0}» solo disponibles = {1}")
    @CsvSource(delimiter = '|', textBlock = """
            ¿Qué libros de ciencia ficción hay disponibles?   | true
            ¿Quedan ejemplares de Dune?                       | true
            Libros de García Márquez                          | false
            """)
    void detectsAvailabilityFilter(String text, boolean onlyAvailable) {
        assertThat(classifier.classify(text).onlyAvailable()).isEqualTo(onlyAvailable);
    }

    /** «Más préstamos activos» ordena por préstamos actuales; sin «activos», por el total histórico. */
    @ParameterizedTest(name = "«{0}» activos = {1}")
    @CsvSource(delimiter = '|', textBlock = """
            ¿Quién tiene más préstamos activos?   | true
            ¿Quién tiene más préstamos?           | false
            """)
    void distinguishesActiveRanking(String text, boolean active) {
        assertThat(classifier.classify(text).activeOnly()).isEqualTo(active);
    }

    /** Normalización: sin tildes, sin signos y en minúsculas. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            ¿Qué   Préstamos  están VENCIDOS?!  | que prestamos estan vencidos
            «Cien años de soledad»              | cien anos de soledad
            """)
    void normalizes(String text, String expected) {
        assertThat(IntentClassifier.normalize(text)).isEqualTo(expected);
    }
}
