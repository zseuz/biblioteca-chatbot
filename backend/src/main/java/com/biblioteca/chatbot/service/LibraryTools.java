package com.biblioteca.chatbot.service;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.biblioteca.chatbot.client.BibliotecaApiClient;
import com.biblioteca.chatbot.client.BibliotecaApiException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Herramientas que Claude puede usar para consultar la biblioteca. Todas son de <b>solo
 * lectura</b> y pasan por {@link BibliotecaApiClient}, es decir, por la API REST de la
 * biblioteca con sus mismas reglas de búsqueda y estados.
 *
 * <p>Cada herramienta devuelve JSON con un tope de {@value #MAX_RESULTS} elementos y el total
 * real, para que el modelo sepa cuándo hay más resultados sin llenar su contexto.
 */
@Component
public class LibraryTools {

    /** Elementos máximos que devuelve una herramienta en una sola llamada. */
    static final int MAX_RESULTS = 20;
    /** Estados que admite la API de préstamos. */
    private static final List<String> LOAN_STATUSES = List.of("ALL", "ACTIVE", "OVERDUE", "RETURNED");

    /** Acceso a la API de la biblioteca. */
    private final BibliotecaApiClient biblioteca;
    /** Construye y serializa el JSON que lee el modelo. */
    private final JsonMapper json;

    /** Inyección por constructor. */
    public LibraryTools(BibliotecaApiClient biblioteca, JsonMapper json) {
        this.biblioteca = biblioteca;
        this.json = json;
    }

    /**
     * Definiciones que se envían a Claude. Con {@code strict} el modelo siempre manda argumentos
     * que cumplen el esquema, así que todos los campos son obligatorios (cadena vacía = sin filtro).
     */
    public List<Tool> definitions() {
        return List.of(
                tool("buscar_libros",
                        "Busca libros del catálogo por título, autor o género (sin distinguir mayúsculas ni "
                                + "tildes). Devuelve ejemplares totales y disponibles de cada libro. Usa texto vacío "
                                + "para listar el catálogo.",
                        ordered("texto", string("Parte del título, autor o género; cadena vacía para todos"))),
                tool("buscar_prestamos",
                        "Busca en el historial de préstamos, del más reciente al más antiguo. Cada préstamo trae "
                                + "libro, usuario, fechas y estado: ACTIVE (en plazo), OVERDUE (vencido) o RETURNED "
                                + "(devuelto). Devuelve " + MAX_RESULTS + " por página y el total.",
                        ordered(
                                "estado", withEnum(string("Filtro por estado; ALL para todos"),
                                        LOAN_STATUSES),
                                "texto", string("Parte del título del libro o del nombre del usuario; "
                                        + "cadena vacía para todos"),
                                "pagina", schema("integer", "Página de resultados, empezando en 0"))),
                tool("buscar_usuarios",
                        "Busca usuarios de la biblioteca por nombre o correo (sin distinguir mayúsculas ni tildes). "
                                + "Incluye cuántos préstamos tiene activos y en total. Usa texto vacío para listarlos.",
                        ordered("texto", string("Parte del nombre o del correo; cadena vacía para todos"))),
                tool("estadisticas",
                        "Indicadores generales: total de libros y usuarios, préstamos en plazo, vencidos y "
                                + "devueltos, préstamos de los últimos 6 meses, libros más prestados, préstamos por "
                                + "género y usuarios más activos.",
                        ordered()));
    }

    /**
     * Ejecuta una herramienta y devuelve su resultado en JSON.
     *
     * @throws IllegalArgumentException si la herramienta no existe o un argumento no es válido
     * @throws BibliotecaApiException   si la API de la biblioteca rechaza la consulta o no responde
     */
    public String execute(String name, Map<String, Object> input) {
        JsonNode result = switch (name) {
            case "buscar_libros" -> limited("libros", biblioteca.books(text(input, "texto")));
            case "buscar_prestamos" -> biblioteca.loans(status(input), text(input, "texto"), page(input), MAX_RESULTS);
            case "buscar_usuarios" -> limited("usuarios", filterMembers(biblioteca.members(), text(input, "texto")));
            case "estadisticas" -> biblioteca.stats();
            default -> throw new IllegalArgumentException("Herramienta desconocida: " + name);
        };
        return json.writeValueAsString(result);
    }

    /**
     * La API de usuarios no tiene búsqueda: se filtra aquí por nombre o correo, sin distinguir
     * mayúsculas ni tildes («perez» encuentra «Pérez»).
     */
    private JsonNode filterMembers(JsonNode members, String text) {
        String q = normalize(text.trim());
        List<JsonNode> matches = new ArrayList<>();
        for (JsonNode m : members) {
            if (q.isEmpty() || normalize(m.path("name").asString("")).contains(q)
                    || normalize(m.path("email").asString("")).contains(q)) {
                matches.add(m);
            }
        }
        return json.valueToTree(matches);
    }

    /** Recorta una lista a {@link #MAX_RESULTS} y añade el total real. */
    private ObjectNode limited(String key, JsonNode items) {
        ObjectNode result = json.createObjectNode();
        result.put("total", items.size());
        ArrayNode first = result.putArray(key);
        for (int i = 0; i < Math.min(items.size(), MAX_RESULTS); i++) {
            first.add(items.get(i));
        }
        if (items.size() > MAX_RESULTS) {
            result.put("nota", "Se muestran los primeros " + MAX_RESULTS + "; afina la búsqueda para ver otros.");
        }
        return result;
    }

    /** Minúsculas y sin tildes ({@code "Díaz"} → {@code "diaz"}). */
    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    /** Argumento de texto; ausente o no textual cuenta como vacío. */
    private static String text(Map<String, Object> input, String key) {
        return input.get(key) instanceof String s ? s : "";
    }

    /** Estado de préstamo validado antes de llamar a la API. */
    private static String status(Map<String, Object> input) {
        String status = text(input, "estado").trim().toUpperCase(Locale.ROOT);
        if (status.isEmpty()) {
            return "ALL";
        }
        if (!LOAN_STATUSES.contains(status)) {
            throw new IllegalArgumentException("Estado no válido: " + status + ". Usa ALL, ACTIVE, OVERDUE o RETURNED.");
        }
        return status;
    }

    /** Número de página; ausente, negativo o inválido cuenta como la primera. */
    private static int page(Map<String, Object> input) {
        return input.get("pagina") instanceof Number n ? Math.max(0, n.intValue()) : 0;
    }

    /**
     * Construye la definición estricta de una herramienta: todas sus propiedades son
     * obligatorias y no se admiten otras.
     */
    private static Tool tool(String name, String description, Map<String, Map<String, Object>> properties) {
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        properties.forEach((key, schema) -> props.putAdditionalProperty(key, JsonValue.from(schema)));
        return Tool.builder()
                .name(name)
                .description(description)
                .strict(true)
                .inputSchema(Tool.InputSchema.builder()
                        .properties(props.build())
                        .required(List.copyOf(properties.keySet()))
                        .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                        .build())
                .build();
    }

    /*
     * Los esquemas se arman con mapas ordenados: el JSON enviado es idéntico en cada petición
     * (y entre reinicios), requisito para que la API de Claude reutilice la caché de prompts.
     */

    private static Map<String, Object> string(String description) {
        return schema("string", description);
    }

    private static Map<String, Object> schema(String type, String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("description", description);
        return schema;
    }

    private static Map<String, Object> withEnum(Map<String, Object> schema, List<String> values) {
        schema.put("enum", values);
        return schema;
    }

    /** Propiedades en el orden indicado (pares nombre, esquema). */
    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> ordered(Object... pairs) {
        Map<String, Map<String, Object>> props = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            props.put((String) pairs[i], (Map<String, Object>) pairs[i + 1]);
        }
        return props;
    }
}
