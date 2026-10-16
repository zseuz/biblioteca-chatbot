package com.biblioteca.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.biblioteca.chatbot.client.BibliotecaApiClient;
import com.biblioteca.chatbot.client.BibliotecaApiException;
import com.biblioteca.chatbot.config.BibliotecaProperties;
import com.biblioteca.chatbot.exception.BadRequestException;
import com.biblioteca.chatbot.model.Book;
import com.biblioteca.chatbot.model.ChatMessage;
import com.biblioteca.chatbot.model.Loan;
import com.biblioteca.chatbot.model.LoanPage;
import com.biblioteca.chatbot.model.Member;
import com.biblioteca.chatbot.model.Stats;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Respuestas del asistente con la API de la biblioteca simulada (Mockito) y la fecha fija en el
 * 15/10/2026: qué se consulta para cada pregunta y cómo se redacta la respuesta.
 */
class ChatServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);
    private static final Member ANA = new Member(1L, "Ana Torres", "ana@example.com", 2, 7);
    private static final Member LUIS = new Member(2L, "Luis Gómez", "luis@example.com", 0, 3);
    private static final Member MARTA = new Member(3L, "Marta Ruiz", "marta@example.com", 3, 4);
    private static final Book DUNE = new Book(1L, "Dune", "Frank Herbert", "Ciencia ficción", 3, 2);
    private static final Book FUNDACION = new Book(2L, "Fundación", "Isaac Asimov", "Ciencia ficción", 1, 0);

    private BibliotecaApiClient biblioteca;
    private ChatService service;

    @BeforeEach
    void setUp() {
        biblioteca = mock(BibliotecaApiClient.class);
        when(biblioteca.members()).thenReturn(List.of(ANA, LUIS, MARTA));
        Clock clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
        service = new ChatService(new IntentClassifier(), biblioteca,
                new BibliotecaProperties("http://localhost:8080/api", Duration.ofSeconds(5), 14, 3, 5), clock);
    }

    @Test
    void overdueLoansListsEachWithDaysLate() {
        when(biblioteca.loans("OVERDUE", "", 100)).thenReturn(new LoanPage(List.of(
                loan("Rayuela", "Ana Torres", TODAY.minusDays(4), "OVERDUE")), 1));

        assertThat(ask("¿Qué préstamos están vencidos?")).isEqualTo("""
                Hay 1 préstamo vencido:
                - «Rayuela» – Ana Torres, venció el 11/10/2026 (4 días de retraso)""");
    }

    @Test
    void overdueLoansOfMember() {
        when(biblioteca.loans("OVERDUE", "María", 100)).thenReturn(new LoanPage(List.of(
                loan("Dune", "María Gómez", TODAY.minusDays(1), "OVERDUE")), 1));

        assertThat(ask("préstamos vencidos de María")).isEqualTo("""
                Hay 1 préstamo vencido que coincide con «María»:
                - «Dune» – María Gómez, venció el 14/10/2026 (1 día de retraso)""");
    }

    @Test
    void noOverdueLoans() {
        when(biblioteca.loans("OVERDUE", "", 100)).thenReturn(new LoanPage(List.of(), 0));
        assertThat(ask("préstamos atrasados")).isEqualTo("No hay préstamos vencidos.");
    }

    @Test
    void singleBookShowsAvailability() {
        when(biblioteca.books("Dune")).thenReturn(List.of(DUNE));
        assertThat(ask("¿Hay ejemplares de Dune?"))
                .isEqualTo("«Dune» de Frank Herbert (Ciencia ficción). Sí hay ejemplares: quedan 2 de 3.");

        when(biblioteca.books("Fundación")).thenReturn(List.of(FUNDACION));
        assertThat(ask("¿Está disponible Fundación?")).endsWith("su único ejemplar está prestado.");

        when(biblioteca.books("Rayuela")).thenReturn(List.of(new Book(3L, "Rayuela", "Julio Cortázar", "Novela", 2, 1)));
        assertThat(ask("¿Quedan ejemplares de Rayuela?")).endsWith("Sí hay ejemplares: queda 1 de 2.");
    }

    @Test
    void genreSearchFiltersAvailableBooks() {
        when(biblioteca.books("ciencia ficción")).thenReturn(List.of(DUNE, FUNDACION));

        assertThat(ask("¿Qué libros de ciencia ficción hay disponibles?")).isEqualTo("""
                1 libro disponible para «ciencia ficción»:
                - «Dune» de Frank Herbert – 2 de 3 disponibles""");
    }

    /** Si no hay libros con ese texto pero sí un usuario, se responde con sus préstamos. */
    @Test
    void searchFallsBackToMember() {
        when(biblioteca.books("Luis")).thenReturn(List.of());
        when(biblioteca.loans("ALL", "Luis Gómez", 100)).thenReturn(new LoanPage(List.of(), 0));

        assertThat(ask("Luis")).isEqualTo(
                "Luis Gómez no tiene libros prestados ahora mismo. En total ha pedido 3 préstamos.");
    }

    @Test
    void memberLoansListsOpenOnesAndMarksOverdue() {
        when(biblioteca.loans("ALL", "Ana Torres", 100)).thenReturn(new LoanPage(List.of(
                loan("Dune", "Ana Torres", TODAY.plusDays(3), "ACTIVE"),
                loan("Rayuela", "Ana Torres", TODAY.minusDays(2), "OVERDUE"),
                loan("1984", "Ana Torres", TODAY.minusDays(30), "RETURNED")), 3));

        assertThat(ask("¿Qué libros tiene prestados Ana?")).isEqualTo("""
                Ana Torres tiene 2 libros prestados (1 vencido):
                - «Rayuela», vencido desde el 13/10/2026 (2 días de retraso)
                - «Dune», vence el 18/10/2026
                En total ha pedido 7 préstamos.""");
    }

    /** «¿Quién tiene X?» sin usuario con ese nombre: quién tiene prestado el libro. */
    @Test
    void whoHasBook() {
        when(biblioteca.books("Dune")).thenReturn(List.of(DUNE));
        when(biblioteca.loans("ALL", "Dune", 100)).thenReturn(new LoanPage(List.of(
                loan("Dune", "Marta Ruiz", TODAY.plusDays(5), "ACTIVE")), 1));

        assertThat(ask("¿Quién tiene Dune?")).isEqualTo("""
                Tienen prestado ahora mismo:
                - «Dune» – Marta Ruiz, vence el 20/10/2026""");
    }

    /** Varios usuarios coinciden: se pregunta cuál, sin consultar préstamos. */
    @Test
    void ambiguousMemberAsksWhich() {
        when(biblioteca.members()).thenReturn(List.of(ANA, new Member(9L, "Ana Pérez", "aperez@example.com", 0, 0)));

        assertThat(ask("¿Qué préstamos tiene Ana?")).startsWith("Hay 2 usuarios que coinciden con «Ana». ¿Cuál de ellos?");
        verify(biblioteca, never()).loans(anyString(), anyString(), anyInt());
    }

    @Test
    void topActiveMembersSortedByOpenLoans() {
        assertThat(ask("¿Quién tiene más préstamos activos?")).isEqualTo("""
                Usuarios con más préstamos sin devolver:
                - Marta Ruiz: 3 préstamos activos
                - Ana Torres: 2 préstamos activos""");
    }

    @Test
    void statsSummary() {
        when(biblioteca.stats()).thenReturn(new Stats(10, 6, 6, 2, 15, List.of(), List.of(), List.of()));
        assertThat(ask("estadísticas")).isEqualTo("""
                Resumen de la biblioteca:
                - 10 títulos y 6 usuarios.
                - 6 préstamos en plazo, 2 vencidos y 15 devueltos.""");
    }

    @Test
    void topBooksFromStats() {
        when(biblioteca.stats()).thenReturn(new Stats(1, 1, 0, 0, 5, List.of(new Stats.Entry("Dune", 5)),
                List.of(), List.of()));
        assertThat(ask("libros más prestados")).isEqualTo("""
                Libros más prestados:
                - Dune: 5 préstamos""");
    }

    /** Las listas largas se cortan en 10 elementos con «y N más». */
    @Test
    void longListsAreTruncated() {
        List<Loan> loans = IntStream.range(0, 12)
                .mapToObj(i -> loan("Libro " + i, "Ana Torres", TODAY.plusDays(i), "ACTIVE")).toList();
        when(biblioteca.loans("ACTIVE", "", 100)).thenReturn(new LoanPage(loans, 12));

        String reply = ask("préstamos en plazo");
        assertThat(reply).startsWith("Hay 12 préstamos en plazo");
        assertThat(reply.lines().filter(l -> l.startsWith("- «"))).hasSize(ChatService.MAX_ITEMS);
        assertThat(reply).endsWith("- y 2 más");
    }

    /** Reglas, ayuda, acciones y saludos se responden sin consultar la biblioteca. */
    @Test
    void fixedAnswersDoNotCallBiblioteca() {
        when(biblioteca.members()).thenReturn(List.of());
        BibliotecaApiClient untouched = mock(BibliotecaApiClient.class);
        ChatService offline = new ChatService(new IntentClassifier(), untouched,
                new BibliotecaProperties("x", Duration.ofSeconds(1), 14, 3, 5), Clock.systemUTC());

        assertThat(offline.reply(List.of(user("¿Cuánto dura un préstamo?"))).reply())
                .contains("14 días", "máximo 3 préstamos", "faltan 5 días o menos");
        assertThat(offline.reply(List.of(user("Presta Dune a Ana"))).reply()).startsWith("Solo puedo consultar");
        assertThat(offline.reply(List.of(user("hola"))).reply()).startsWith("¡Hola!");
        assertThat(offline.reply(List.of(user("ayuda"))).reply()).isEqualTo(ChatService.HELP);
        verifyNoInteractions(untouched);
    }

    /** Solo cuenta la última pregunta, y debe ser del usuario. */
    @Test
    void usesLastUserMessage() {
        when(biblioteca.loans(eq("OVERDUE"), anyString(), anyInt())).thenReturn(new LoanPage(List.of(), 0));
        String reply = service.reply(List.of(user("hola"), new ChatMessage("assistant", "¡Hola!"),
                user("¿Hay vencidos?"))).reply();
        assertThat(reply).isEqualTo("No hay préstamos vencidos.");

        assertThatThrownBy(() -> service.reply(List.of(user("hola"), new ChatMessage("assistant", "¡Hola!"))))
                .isInstanceOf(BadRequestException.class);
    }

    /** Si la biblioteca no responde, el error llega al controlador (que responde 503). */
    @Test
    void bibliotecaErrorsPropagate() {
        when(biblioteca.stats()).thenThrow(new BibliotecaApiException("sin conexión"));
        assertThatThrownBy(() -> ask("estadísticas")).isInstanceOf(BibliotecaApiException.class);
    }

    private String ask(String question) {
        return service.reply(List.of(user(question))).reply();
    }

    private static ChatMessage user(String text) {
        return new ChatMessage("user", text);
    }

    private static Loan loan(String title, String member, LocalDate due, String status) {
        return new Loan(1L, title, member, due.minusDays(14), due, "RETURNED".equals(status) ? due : null, status);
    }
}
