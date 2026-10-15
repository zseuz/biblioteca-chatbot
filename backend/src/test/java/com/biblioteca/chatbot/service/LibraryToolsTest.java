package com.biblioteca.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Tool;
import com.biblioteca.chatbot.client.BibliotecaApiClient;
import com.biblioteca.chatbot.client.BibliotecaApiException;
import com.biblioteca.chatbot.config.BibliotecaProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Herramientas del asistente contra una API de biblioteca simulada (MockRestServiceServer):
 * qué URL se consulta con cada herramienta, cómo se recortan y filtran los resultados y qué
 * pasa cuando la biblioteca responde con error.
 */
class LibraryToolsTest {

    private static final String BASE = "http://biblioteca.test/api";

    private final JsonMapper json = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private BibliotecaApiClient client;
    private LibraryTools tools;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        BibliotecaProperties props = new BibliotecaProperties(BASE, Duration.ofSeconds(1), 14, 3, 5);
        client = new BibliotecaApiClient(builder.build(), json, props);
        tools = new LibraryTools(client, json);
    }

    /** buscar_libros consulta /books con el texto codificado y devuelve el total y los libros. */
    @Test
    void searchesBooksWithEncodedQuery() {
        server.expect(requestTo(BASE + "/books?q=garc%C3%ADa%20%26%20cia")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"title\":\"Cien años de soledad\",\"availableCopies\":1}]",
                        MediaType.APPLICATION_JSON));

        JsonNode result = run("buscar_libros", Map.of("texto", "garcía & cia"));

        assertThat(result.get("total").asInt()).isEqualTo(1);
        assertThat(result.get("libros").get(0).get("title").asString()).isEqualTo("Cien años de soledad");
        server.verify();
    }

    /** Con muchos resultados se devuelven solo los primeros 20, el total real y una nota. */
    @Test
    void limitsLongResults() {
        String books = IntStream.range(0, 25).mapToObj(i -> "{\"id\":" + i + "}")
                .collect(Collectors.joining(",", "[", "]"));
        server.expect(requestTo(BASE + "/books?q=")).andRespond(withSuccess(books, MediaType.APPLICATION_JSON));

        JsonNode result = run("buscar_libros", Map.of("texto", ""));

        assertThat(result.get("total").asInt()).isEqualTo(25);
        assertThat(result.get("libros").size()).isEqualTo(LibraryTools.MAX_RESULTS);
        assertThat(result.get("nota").asString()).contains("primeros 20");
    }

    /** buscar_prestamos pasa estado, texto, página y tamaño a /loans. */
    @Test
    void searchesLoansWithFilters() {
        server.expect(requestTo(BASE + "/loans?status=OVERDUE&q=dune&page=1&size=20"))
                .andRespond(withSuccess("{\"content\":[],\"totalElements\":0}", MediaType.APPLICATION_JSON));

        JsonNode result = run("buscar_prestamos", Map.of("estado", "overdue", "texto", "dune", "pagina", 1));

        assertThat(result.get("totalElements").asInt()).isZero();
        server.verify();
    }

    /** Un estado inventado se rechaza antes de llamar a la biblioteca. */
    @Test
    void rejectsUnknownLoanStatus() {
        assertThatThrownBy(() -> tools.execute("buscar_prestamos", Map.of("estado", "PERDIDO", "texto", "", "pagina", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERDIDO");
        server.verify();
    }

    /** La API no busca usuarios: se filtra aquí por nombre o correo, sin tildes ni mayúsculas. */
    @Test
    void filtersMembersByNameOrEmail() {
        server.expect(requestTo(BASE + "/members")).andRespond(withSuccess("""
                [{"name":"Ana Pérez","email":"ana@example.com","activeLoans":2},
                 {"name":"Luis","email":"luis@example.com","activeLoans":0},
                 {"name":"Marta","email":"PEREZ.marta@example.com","activeLoans":1}]
                """, MediaType.APPLICATION_JSON));

        JsonNode result = run("buscar_usuarios", Map.of("texto", "PEREZ"));

        assertThat(result.get("total").asInt()).isEqualTo(2);
        assertThat(result.get("usuarios").get(0).get("name").asString()).isEqualTo("Ana Pérez");
        assertThat(result.get("usuarios").get(1).get("name").asString()).isEqualTo("Marta");
    }

    /** estadisticas devuelve el panel de /stats tal cual. */
    @Test
    void returnsStats() {
        server.expect(requestTo(BASE + "/stats"))
                .andRespond(withSuccess("{\"totalBooks\":10,\"overdueLoans\":2}", MediaType.APPLICATION_JSON));

        assertThat(run("estadisticas", Map.of()).get("overdueLoans").asInt()).isEqualTo(2);
    }

    /** Si la biblioteca responde con error, el mensaje de su API llega al modelo. */
    @Test
    void reportsBibliotecaErrorMessage() {
        server.expect(requestTo(BASE + "/stats")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":500,\"message\":\"Error interno del servidor\"}"));

        assertThatThrownBy(() -> tools.execute("estadisticas", Map.of()))
                .isInstanceOf(BibliotecaApiException.class)
                .hasMessageContaining("HTTP 500")
                .hasMessageContaining("Error interno del servidor");
    }

    /** Una herramienta inexistente falla sin llamar a la biblioteca. */
    @Test
    void unknownToolFails() {
        assertThatThrownBy(() -> tools.execute("borrar_todo", Map.of())).isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    /**
     * Las definiciones son estrictas (todas las propiedades obligatorias y ninguna adicional) y
     * cada una corresponde a una herramienta que {@link LibraryTools#execute} sabe ejecutar.
     */
    @Test
    void definitionsAreStrict() {
        assertThat(tools.definitions()).extracting(Tool::name)
                .containsExactly("buscar_libros", "buscar_prestamos", "buscar_usuarios", "estadisticas");
        for (Tool tool : tools.definitions()) {
            assertThat(tool.strict()).contains(true);
            Tool.InputSchema schema = tool.inputSchema();
            assertThat(schema._additionalProperties().get("additionalProperties")).isEqualTo(JsonValue.from(false));
            Map<String, JsonValue> props = schema.properties().map(p -> p._additionalProperties()).orElse(Map.of());
            assertThat(schema.required().orElse(List.of())).containsExactlyInAnyOrderElementsOf(props.keySet());
        }
    }

    /** El chequeo de estado devuelve false si la biblioteca no responde. */
    @Test
    void availabilityCheck() {
        server.expect(requestTo(BASE + "/loans/summary")).andRespond(withResourceNotFound());
        assertThat(client.isAvailable()).isFalse();
    }

    private JsonNode run(String tool, Map<String, Object> input) {
        return json.readTree(tools.execute(tool, input));
    }
}
