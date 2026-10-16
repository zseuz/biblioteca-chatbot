package com.biblioteca.chatbot.service;

import com.biblioteca.chatbot.model.Intent;
import com.biblioteca.chatbot.model.Question;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Interpreta una pregunta escrita en lenguaje natural con reglas de palabras clave (sin IA).
 *
 * <p>Funciona en dos pasos:
 * <ol>
 *   <li><b>Intención</b>: se busca en el texto normalizado (minúsculas, sin tildes ni signos)
 *       qué se pregunta, comprobando las reglas de la más concreta a la más general.</li>
 *   <li><b>Sujeto</b>: se quitan del principio y del final las palabras «de relleno» (qué, hay,
 *       libros, préstamos, de…) y lo que queda es el nombre, título, autor o género. Solo se
 *       recortan los extremos para no romper títulos como «Cien años <i>de</i> soledad».</li>
 * </ol>
 */
@Component
public class IntentClassifier {

    /** Palabras que no forman parte del sujeto cuando aparecen al principio o al final. */
    private static final Set<String> FILLER = Set.of(
            // preguntas y verbos comunes
            "que", "cual", "cuales", "quien", "quienes", "cuanto", "cuantos", "cuanta", "cuantas", "como", "donde",
            "hay", "habra", "queda", "quedan", "tiene", "tienen", "tengo", "tenemos", "debe", "deben", "lleva",
            "llevan", "pidio", "pidieron", "esta", "estan", "es", "son", "ver", "dime", "decime", "muestra",
            "muestrame", "mostrar", "lista", "listar", "listame", "busca", "buscar", "buscame", "encuentra",
            "quiero", "quisiera", "saber", "conocer", "puedes", "podrias", "me", "nos", "mi", "sus", "su", "se",
            "hola", "por", "favor", "porfa", "porfavor", "gracias", "ahora", "actualmente", "hoy", "mismo", "todavia",
            "aun", "ya", "algo", "alguno", "alguna", "algunos", "algunas", "algun", "todo", "todos", "todas",
            // artículos y preposiciones
            "el", "la", "los", "las", "lo", "un", "una", "unos", "unas", "de", "del", "al", "a", "en", "para", "con",
            "sobre", "y", "o", "e",
            // palabras del dominio que indican el tipo de pregunta, no el sujeto
            "libro", "libros", "titulo", "titulos", "ejemplar", "ejemplares", "copia", "copias", "obra", "obras",
            "autor", "autora", "autores", "genero", "generos", "categoria", "disponible", "disponibles", "libre",
            "libres", "prestamo", "prestamos", "prestado", "prestados", "prestada", "prestadas", "activo", "activos",
            "activa", "activas", "vencido", "vencidos", "vencida", "vencidas", "atrasado", "atrasados", "pendiente",
            "pendientes", "usuario", "usuarios", "usuaria", "lector", "lectores", "lectora", "socio", "socios",
            "persona", "personas", "biblioteca", "catalogo", "plazo", "fuera", "actual", "actuales", "abiertos",
            "sin", "devolver", "devuelto", "devueltos");

    /** Saludo al principio; el grupo «rest» es lo que se escribió después. */
    private static final Pattern GREETING = Pattern.compile(
            "^(hola|holi|buenos dias|buenas tardes|buenas noches|buenas|hey|saludos|que tal)(?<rest>( .*)?)$");
    private static final Pattern ACTION = Pattern.compile(
            "^(por favor )?(presta|prestale|prestar|devuelve|devolver|renueva|renovar|registra|registrar|elimina"
                    + "|eliminar|borra|borrar|agrega|agregar|anade|anadir|crea|crear|da de alta|dar de alta|modifica"
                    + "|modificar|cambia|cambiar|edita|editar)\\b.*"
                    + "|.*\\b(quiero|necesito|puedes|podrias|me puedes) (prestar|devolver|renovar|registrar|eliminar"
                    + "|borrar|agregar|anadir|crear|dar de alta|modificar|editar)\\b.*");

    /** Interpreta una pregunta. Nunca devuelve {@code null}: lo que no se reconoce es {@link Intent#UNKNOWN}. */
    public Question classify(String text) {
        String n = normalize(text);
        if (n.isEmpty()) {
            return Question.of(Intent.HELP);
        }
        List<String> words = words(n);
        String subject = originalSubject(text, words);

        // Solo es un saludo si después no se pregunta nada («hola, ¿hay libros de Dune?» no lo es).
        Matcher greeting = GREETING.matcher(n);
        if (greeting.matches() && subject(words(greeting.group("rest").trim())).isEmpty()
                && !has(greeting.group("rest"), "prestamo", "libro", "usuario", "vencid", "disponible")) {
            return Question.of(Intent.GREETING);
        }
        if (words.size() <= 5 && has(n, "gracias", "muchas gracias", "genial", "perfecto")) {
            return Question.of(Intent.THANKS);
        }
        if (has(n, "ayuda", "que puedes", "que sabes", "como funciona", "que preguntas", "que haces", "opciones",
                "que puedo preguntar", "para que sirves")) {
            return Question.of(Intent.HELP);
        }
        if (isRulesQuestion(n)) {
            return Question.of(Intent.RULES);
        }
        if (ACTION.matcher(n).matches()) {
            return Question.of(Intent.ACTION);
        }
        if (has(n, "vencid", "atrasad", "retrasad", "fuera de plazo", "moros", "no han devuelto", "no devolvieron")) {
            return new Question(Intent.OVERDUE_LOANS, subject, false, true);
        }
        boolean aboutPeople = has(n, "quien", "quienes", "usuario", "lector", "socio", "persona");
        boolean most = has(n, "mas ", "mayor", "top", "ranking", "populares", "mejores");
        if (aboutPeople && most && has(n, "prestamo", "libros", "activ", "lee", "lect", "pide", "pedido")) {
            return new Question(Intent.TOP_MEMBERS, "", false, has(n, "activ", "ahora", "actual", "en este momento"));
        }
        if (has(n, "libro", "titulo", "obra") && most && has(n, "prestad", "popular", "pedid", "leid", "solicitad", "exito")) {
            return Question.of(Intent.TOP_BOOKS);
        }
        if (has(n, "genero", "categoria") && (most || has(n, "por genero", "por categoria"))) {
            return Question.of(Intent.GENRES);
        }
        if (has(n, "estadistic", "resumen", "indicadores", "cifras", "numeros generales")) {
            return Question.of(Intent.STATS);
        }
        boolean aboutLoans = has(n, "prestamo", "prestad", "tiene", "tienen", "debe", "pidio", "lleva");
        if (!subject.isEmpty()) {
            if (aboutLoans) {
                return new Question(Intent.SUBJECT_LOANS, subject, false, true);
            }
            return new Question(Intent.BOOK_SEARCH, subject, has(n, "disponible", "libre", "quedan", "queda"), false);
        }
        // «¿Cuántos libros hay?» (sin sujeto) es una pregunta de totales; con sujeto se trató arriba.
        if (has(n, "cuantos", "cuantas", "total")) {
            return Question.of(Intent.STATS);
        }
        if (aboutLoans || has(n, "en plazo")) {
            return Question.of(Intent.ACTIVE_LOANS);
        }
        if (has(n, "libro", "catalogo", "titulo", "disponible", "ejemplar")) {
            return new Question(Intent.LIST_BOOKS, "", has(n, "disponible", "libre", "quedan", "queda"), false);
        }
        if (has(n, "usuario", "socio", "lector")) {
            return Question.of(Intent.LIST_MEMBERS);
        }
        return Question.of(Intent.UNKNOWN);
    }

    /** Preguntas sobre las reglas: plazo, máximo de préstamos, renovación. «En plazo» no cuenta. */
    private static boolean isRulesQuestion(String n) {
        if (has(n, "cuanto dura", "cuantos dias", "cuanto tiempo", "plazo de", "plazo para", "plazo maximo",
                "maximo de", "limite", "reglas", "normas", "multa", "cuantos libros puede", "cuantos prestamos puede",
                "cuantos libros se pueden", "cuantos prestamos se pueden")) {
            return true;
        }
        return has(n, "renov") && has(n, "cuando", "se puede", "puedo", "como", "cuantas veces", "se pueden");
    }

    /**
     * Quita las palabras de relleno del principio y del final; lo que queda es el sujeto
     * (nombre, título, autor o género) o una cadena vacía si no hay ninguno.
     */
    static String subject(List<String> words) {
        int[] range = subjectRange(words);
        return String.join(" ", words.subList(range[0], range[1]));
    }

    /**
     * El sujeto con las palabras tal como se escribieron («Cien años de soledad», no «cien anos de
     * soledad»), para repetirlo en la respuesta. La búsqueda en la biblioteca ignora igualmente
     * tildes y mayúsculas.
     */
    private static String originalSubject(String text, List<String> words) {
        List<String> original = Arrays.stream(text.split("[^\\p{L}\\p{N}]+")).filter(w -> !w.isEmpty()).toList();
        if (original.size() != words.size()) {
            return subject(words); // no se pudieron alinear: se usa la versión normalizada
        }
        int[] range = subjectRange(words);
        return String.join(" ", original.subList(range[0], range[1]));
    }

    /** Posiciones [inicio, fin) del sujeto tras recortar el relleno de los extremos. */
    private static int[] subjectRange(List<String> words) {
        int start = 0;
        int end = words.size();
        while (start < end && FILLER.contains(words.get(start))) {
            start++;
        }
        while (end > start && FILLER.contains(words.get(end - 1))) {
            end--;
        }
        return new int[] {start, end};
    }

    /** Palabras de un texto ya normalizado (lista vacía si no hay ninguna). */
    private static List<String> words(String n) {
        return n.isEmpty() ? List.of() : Arrays.asList(n.split(" "));
    }

    /** {@code true} si el texto contiene alguno de los fragmentos. */
    private static boolean has(String n, String... fragments) {
        // Con espacios alrededor, un fragmento como "mas " también encuentra la última palabra.
        String padded = " " + n + " ";
        for (String f : fragments) {
            if (padded.contains(f)) {
                return true;
            }
        }
        return false;
    }

    /** Minúsculas, sin tildes y sin signos de puntuación; los espacios repetidos se juntan. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
