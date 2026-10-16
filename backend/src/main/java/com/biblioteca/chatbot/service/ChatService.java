package com.biblioteca.chatbot.service;

import com.biblioteca.chatbot.client.BibliotecaApiClient;
import com.biblioteca.chatbot.client.BibliotecaApiException;
import com.biblioteca.chatbot.config.BibliotecaProperties;
import com.biblioteca.chatbot.exception.BadRequestException;
import com.biblioteca.chatbot.model.Book;
import com.biblioteca.chatbot.model.ChatMessage;
import com.biblioteca.chatbot.model.ChatResponse;
import com.biblioteca.chatbot.model.ChatStatus;
import com.biblioteca.chatbot.model.Loan;
import com.biblioteca.chatbot.model.LoanPage;
import com.biblioteca.chatbot.model.Member;
import com.biblioteca.chatbot.model.Question;
import com.biblioteca.chatbot.model.Stats;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Responde preguntas sobre la biblioteca sin IA: {@link IntentClassifier} decide qué se pregunta
 * y aquí se consulta la API de la biblioteca y se redacta la respuesta en texto plano.
 *
 * <p>No guarda estado entre peticiones ni modifica datos. Si la biblioteca no responde, la
 * {@link BibliotecaApiException} llega al controlador y se devuelve un 503 con el motivo.
 */
@Service
public class ChatService {

    /** Elementos máximos de una lista en la respuesta (el resto se resume con «y N más»). */
    static final int MAX_ITEMS = 10;
    /** Préstamos que se piden a la API en una consulta. */
    private static final int LOAN_PAGE = 100;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Texto de ayuda: qué se puede preguntar. */
    static final String HELP = """
            Puedo responder preguntas como:
            - ¿Qué préstamos están vencidos?
            - ¿Hay ejemplares disponibles de Dune?
            - ¿Qué libros de ciencia ficción hay disponibles?
            - ¿Qué libros tiene prestados Ana?
            - ¿Quién tiene Cien años de soledad?
            - ¿Quién tiene más préstamos activos?
            - ¿Cuáles son los libros más prestados?
            - ¿Cuánto dura un préstamo y cuándo se puede renovar?
            - Estadísticas de la biblioteca""";

    private final IntentClassifier classifier;
    private final BibliotecaApiClient biblioteca;
    /** Reglas de préstamo, para responder «¿cuánto dura un préstamo?». */
    private final BibliotecaProperties rules;
    /** Reloj inyectado: para calcular los días de retraso (fijo en las pruebas). */
    private final Clock clock;

    /** Inyección por constructor. */
    public ChatService(IntentClassifier classifier, BibliotecaApiClient biblioteca, BibliotecaProperties rules,
                       Clock clock) {
        this.classifier = classifier;
        this.biblioteca = biblioteca;
        this.rules = rules;
        this.clock = clock;
    }

    /** Estado actual: si la API de la biblioteca responde. */
    public ChatStatus status() {
        return new ChatStatus(biblioteca.isAvailable());
    }

    /**
     * Responde al último mensaje de la conversación (los anteriores no se usan: cada pregunta se
     * interpreta por sí sola).
     *
     * @throws BadRequestException     si el último mensaje no es del usuario
     * @throws BibliotecaApiException  si la API de la biblioteca no responde
     */
    public ChatResponse reply(List<ChatMessage> conversation) {
        if (conversation.isEmpty() || !conversation.getLast().fromUser()) {
            throw new BadRequestException("El último mensaje de la conversación debe ser del usuario");
        }
        Question q = classifier.classify(conversation.getLast().content());
        String reply = switch (q.intent()) {
            case GREETING -> "¡Hola! Soy el asistente de la biblioteca.\n\n" + HELP;
            case THANKS -> "¡De nada! Si necesitas algo más, pregúntame.";
            case HELP -> HELP;
            case RULES -> rules();
            case ACTION -> "Solo puedo consultar datos, no modificarlos. Para prestar, devolver o renovar usa la "
                    + "pantalla Préstamos de la aplicación de la biblioteca; para libros y usuarios, sus pantallas "
                    + "Libros y Usuarios.";
            case OVERDUE_LOANS -> overdueLoans(q.subject());
            case ACTIVE_LOANS -> activeLoans();
            case TOP_MEMBERS -> topMembers(q.activeOnly());
            case TOP_BOOKS -> ranking("Libros más prestados", biblioteca.stats().topBooks(), "préstamo", "préstamos");
            case GENRES -> ranking("Préstamos por género", biblioteca.stats().loansByGenre(), "préstamo", "préstamos");
            case STATS -> stats();
            case LIST_BOOKS -> listBooks(q.onlyAvailable());
            case LIST_MEMBERS -> listMembers();
            case SUBJECT_LOANS -> subjectLoans(q.subject());
            case BOOK_SEARCH -> bookSearch(q.subject(), q.onlyAvailable());
            case UNKNOWN -> "No he entendido la pregunta. " + HELP;
        };
        return new ChatResponse(reply);
    }

    // --- respuestas -----------------------------------------------------------------------

    private String rules() {
        return """
                Reglas de préstamo:
                - Cada préstamo dura %d días.
                - Un usuario puede tener como máximo %d préstamos a la vez.
                - Con algún préstamo vencido no se pueden pedir más libros hasta devolverlo.
                - Solo se presta un libro si tiene ejemplares disponibles.
                - Un préstamo en plazo se puede renovar cuando faltan %d días o menos para su vencimiento; \
                la renovación da de nuevo %d días desde ese día.""".formatted(
                rules.loanDays(), rules.maxActiveLoans(), rules.renewalWindowDays(), rules.loanDays());
    }

    /** Préstamos vencidos de toda la biblioteca, o solo de un usuario o libro. */
    private String overdueLoans(String subject) {
        LoanPage page = biblioteca.loans("OVERDUE", subject, LOAN_PAGE);
        if (page.totalElements() == 0) {
            return "No hay préstamos vencidos" + (subject.isEmpty() ? "" : " que coincidan con «" + subject + "»") + ".";
        }
        String of = subject.isEmpty() ? ""
                : (page.totalElements() == 1 ? " que coincide" : " que coinciden") + " con «" + subject + "»";
        String header = "Hay " + count(page.totalElements(), "préstamo vencido", "préstamos vencidos") + of + ":";
        return list(header, page.content(), page.totalElements(), l -> "«" + l.bookTitle() + "» – " + l.memberName()
                + ", venció el " + date(l.dueDate()) + " (" + daysLate(l) + ")");
    }

    /** Préstamos en plazo, ordenados por la fecha de vencimiento más cercana. */
    private String activeLoans() {
        LoanPage page = biblioteca.loans("ACTIVE", "", LOAN_PAGE);
        if (page.totalElements() == 0) {
            return "No hay préstamos en plazo ahora mismo.";
        }
        List<Loan> sorted = page.content().stream().sorted(Comparator.comparing(Loan::dueDate)).toList();
        String header = "Hay " + count(page.totalElements(), "préstamo en plazo", "préstamos en plazo")
                + " (sin contar los vencidos):";
        return list(header, sorted, page.totalElements(),
                l -> "«" + l.bookTitle() + "» – " + l.memberName() + ", vence el " + date(l.dueDate()));
    }

    /** Usuarios con más préstamos, ahora mismo o en total según la pregunta. */
    private String topMembers(boolean activeOnly) {
        Function<Member, Long> key = activeOnly ? Member::activeLoans : Member::totalLoans;
        List<Member> ranked = biblioteca.members().stream()
                .filter(m -> key.apply(m) > 0)
                .sorted(Comparator.comparing(key).reversed().thenComparing(Member::name))
                .limit(5)
                .toList();
        if (ranked.isEmpty()) {
            return activeOnly ? "Ahora mismo nadie tiene libros prestados." : "Todavía no hay préstamos registrados.";
        }
        String header = activeOnly ? "Usuarios con más préstamos sin devolver:" : "Usuarios con más préstamos en total:";
        return list(header, ranked, ranked.size(), m -> m.name() + ": "
                + count(key.apply(m), activeOnly ? "préstamo activo" : "préstamo", activeOnly ? "préstamos activos" : "préstamos"));
    }

    /** Ranking del panel de estadísticas (libros más prestados o préstamos por género). */
    private String ranking(String title, List<Stats.Entry> entries, String one, String many) {
        if (entries.isEmpty()) {
            return "Todavía no hay préstamos registrados.";
        }
        return list(title + ":", entries, entries.size(), e -> e.label() + ": " + count(e.count(), one, many));
    }

    private String stats() {
        Stats s = biblioteca.stats();
        return """
                Resumen de la biblioteca:
                - %s y %s.
                - %s, %s y %s.""".formatted(
                count(s.totalBooks(), "título", "títulos"), count(s.totalMembers(), "usuario", "usuarios"),
                count(s.activeLoans(), "préstamo en plazo", "préstamos en plazo"),
                count(s.overdueLoans(), "vencido", "vencidos"),
                count(s.returnedLoans(), "devuelto", "devueltos"));
    }

    /** Catálogo completo o solo los libros con ejemplares disponibles. */
    private String listBooks(boolean onlyAvailable) {
        List<Book> books = biblioteca.books("").stream()
                .filter(b -> !onlyAvailable || b.available())
                .toList();
        if (books.isEmpty()) {
            return onlyAvailable ? "Ahora mismo no queda ningún libro con ejemplares disponibles."
                    : "El catálogo está vacío.";
        }
        String header = onlyAvailable
                ? count(books.size(), "libro tiene", "libros tienen") + " ejemplares disponibles:"
                : "El catálogo tiene " + count(books.size(), "título", "títulos") + ":";
        return list(header, books, books.size(), ChatService::bookLine);
    }

    private String listMembers() {
        List<Member> members = biblioteca.members();
        if (members.isEmpty()) {
            return "No hay usuarios registrados.";
        }
        return list("Hay " + count(members.size(), "usuario", "usuarios") + ":", members, members.size(),
                m -> m.name() + " (" + count(m.activeLoans(), "préstamo activo", "préstamos activos") + ")");
    }

    /**
     * «¿Qué tiene Ana?» o «¿Quién tiene Dune?»: primero se busca un usuario con ese nombre; si no
     * hay, se busca quién tiene prestado un libro con ese título.
     */
    private String subjectLoans(String subject) {
        List<Member> members = findMembers(subject);
        if (members.size() > 1) {
            return whichMember(subject, members);
        }
        if (members.size() == 1) {
            return memberLoans(members.getFirst());
        }
        List<Book> books = biblioteca.books(subject);
        if (books.isEmpty()) {
            return notFound(subject);
        }
        List<Loan> open = biblioteca.loans("ALL", subject, LOAN_PAGE).content().stream()
                .filter(Loan::open)
                .filter(l -> books.stream().anyMatch(b -> b.title().equalsIgnoreCase(l.bookTitle())))
                .toList();
        if (open.isEmpty()) {
            return books.size() == 1
                    ? "Nadie tiene prestado «" + books.getFirst().title() + "» ahora mismo. " + availability(books.getFirst())
                    : "Ninguno de los libros que coinciden con «" + subject + "» está prestado ahora mismo.";
        }
        return list("Tienen prestado ahora mismo:", open, open.size(), l -> "«" + l.bookTitle() + "» – "
                + l.memberName() + (l.overdue() ? ", vencido desde el " : ", vence el ") + date(l.dueDate()));
    }

    /** Préstamos sin devolver de un usuario, con su total histórico. */
    private String memberLoans(Member m) {
        List<Loan> open = biblioteca.loans("ALL", m.name(), LOAN_PAGE).content().stream()
                .filter(l -> l.memberName().equalsIgnoreCase(m.name()) && l.open())
                .sorted(Comparator.comparing(Loan::dueDate))
                .toList();
        String history = " En total ha pedido " + count(m.totalLoans(), "préstamo", "préstamos") + ".";
        if (open.isEmpty()) {
            return m.name() + " no tiene libros prestados ahora mismo." + history;
        }
        long overdue = open.stream().filter(Loan::overdue).count();
        String header = m.name() + " tiene " + count(open.size(), "libro prestado", "libros prestados")
                + (overdue > 0 ? " (" + count(overdue, "vencido", "vencidos") + ")" : "") + ":";
        return list(header, open, open.size(), l -> "«" + l.bookTitle() + "»"
                + (l.overdue() ? ", vencido desde el " + date(l.dueDate()) + " (" + daysLate(l) + ")"
                        : ", vence el " + date(l.dueDate()))) + "\n" + history.trim();
    }

    /**
     * Búsqueda por título, autor o género con la disponibilidad de cada libro. Si no hay libros,
     * se prueba si es el nombre de un usuario.
     */
    private String bookSearch(String subject, boolean onlyAvailable) {
        List<Book> books = biblioteca.books(subject);
        if (books.isEmpty()) {
            List<Member> members = findMembers(subject);
            if (members.size() == 1) {
                return memberLoans(members.getFirst());
            }
            return members.isEmpty() ? notFound(subject) : whichMember(subject, members);
        }
        if (books.size() == 1) {
            Book b = books.getFirst();
            return "«" + b.title() + "» de " + b.author() + " (" + b.genre() + "). " + availability(b);
        }
        List<Book> shown = onlyAvailable ? books.stream().filter(Book::available).toList() : books;
        if (shown.isEmpty()) {
            return "Hay " + count(books.size(), "libro", "libros") + " que coinciden con «" + subject
                    + "», pero ninguno tiene ejemplares disponibles ahora mismo.";
        }
        String header = onlyAvailable
                ? count(shown.size(), "libro disponible", "libros disponibles") + " para «" + subject + "»:"
                : count(books.size(), "libro coincide", "libros coinciden") + " con «" + subject + "»:";
        return list(header, shown, shown.size(), ChatService::bookLine);
    }

    // --- utilidades -----------------------------------------------------------------------

    /** Usuarios cuyo nombre o correo contiene el texto (sin tildes ni mayúsculas). */
    private List<Member> findMembers(String subject) {
        String q = IntentClassifier.normalize(subject);
        return biblioteca.members().stream()
                .filter(m -> IntentClassifier.normalize(m.name()).contains(q)
                        || IntentClassifier.normalize(m.email()).contains(q))
                .toList();
    }

    private String whichMember(String subject, List<Member> members) {
        return list("Hay " + count(members.size(), "usuario", "usuarios") + " que coinciden con «" + subject
                + "». ¿Cuál de ellos?", members, members.size(), Member::name);
    }

    private static String notFound(String subject) {
        return "No encontré libros ni usuarios que coincidan con «" + subject + "». Prueba con otra parte del "
                + "título, el autor, el género o el nombre del usuario.";
    }

    private static String availability(Book b) {
        if (b.available()) {
            return "Sí hay ejemplares: " + (b.availableCopies() == 1 ? "queda " : "quedan ") + b.availableCopies()
                    + " de " + b.totalCopies() + ".";
        }
        return "Ahora mismo no queda ninguno: " + (b.totalCopies() == 1 ? "su único ejemplar está prestado."
                : "los " + b.totalCopies() + " ejemplares están prestados.");
    }

    private static String bookLine(Book b) {
        return "«" + b.title() + "» de " + b.author() + " – "
                + (b.available() ? b.availableCopies() + " de " + b.totalCopies() + " disponibles" : "sin ejemplares disponibles");
    }

    /** Días de retraso de un préstamo vencido, en texto. */
    private String daysLate(Loan l) {
        long days = ChronoUnit.DAYS.between(l.dueDate(), LocalDate.now(clock));
        return days <= 0 ? "vence hoy" : count(days, "día", "días") + " de retraso";
    }

    /** Cabecera más una línea «- » por elemento, con un máximo de {@link #MAX_ITEMS}. */
    private static <T> String list(String header, List<T> items, long total, Function<T, String> line) {
        String body = items.stream().limit(MAX_ITEMS).map(i -> "- " + line.apply(i)).collect(Collectors.joining("\n"));
        long rest = total - Math.min(items.size(), MAX_ITEMS);
        return header + "\n" + body + (rest > 0 ? "\n- y " + rest + " más" : "");
    }

    private static String count(long n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    private static String date(LocalDate d) {
        return d == null ? "—" : d.format(DATE);
    }
}
