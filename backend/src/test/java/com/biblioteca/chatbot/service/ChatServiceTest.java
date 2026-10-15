package com.biblioteca.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.services.blocking.MessageService;
import com.biblioteca.chatbot.client.BibliotecaApiClient;
import com.biblioteca.chatbot.config.BibliotecaProperties;
import com.biblioteca.chatbot.config.ChatProperties;
import com.biblioteca.chatbot.exception.BadRequestException;
import com.biblioteca.chatbot.exception.ServiceUnavailableException;
import com.biblioteca.chatbot.model.ChatMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Pruebas unitarias del bucle del asistente, sin red: la API de Claude se simula con respuestas
 * JSON escritas a mano (mismo formato que devuelve la API real) y las herramientas con un mock.
 * Así se verifica qué se envía a Claude en cada vuelta y cómo se interpreta cada respuesta.
 */
class ChatServiceTest {

    private static final int MAX_TOOL_ROUNDS = 2;

    private MessageService api;
    private LibraryTools tools;
    private ObjectProvider<AnthropicClient> provider;
    private ChatService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        api = mock(MessageService.class);
        AnthropicClient client = mock(AnthropicClient.class);
        when(client.messages()).thenReturn(api);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        tools = mock(LibraryTools.class);
        when(tools.definitions()).thenReturn(List.of());

        Clock clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
        service = new ChatService(provider, tools, mock(BibliotecaApiClient.class),
                new ChatProperties("clave-de-prueba", "claude-opus-5-5", MAX_TOOL_ROUNDS, 4),
                new BibliotecaProperties("http://localhost:8080/api", Duration.ofSeconds(5), 14, 3, 5), clock);
    }

    /**
     * Recorrido típico: Claude pide una herramienta, se ejecuta con sus argumentos, se le
     * devuelve el resultado vinculado a su id y con eso responde en texto.
     */
    @Test
    void runsRequestedToolAndReturnsFinalAnswer() {
        when(api.create(any(MessageCreateParams.class))).thenReturn(
                toolUse("toolu_1", "buscar_libros", "{\"texto\":\"dune\"}"),
                text("Sí, quedan 2 ejemplares de Dune."));
        when(tools.execute(eq("buscar_libros"), anyMap())).thenReturn("{\"total\":1}");

        String reply = service.reply(List.of(user("¿Queda algún Dune?"))).reply();

        assertThat(reply).isEqualTo("Sí, quedan 2 ejemplares de Dune.");
        verify(tools).execute("buscar_libros", Map.of("texto", "dune"));

        List<MessageCreateParams> requests = requests(2);
        List<MessageParam> second = requests.get(1).messages();
        // pregunta + petición de herramienta + resultado
        assertThat(second).hasSize(3);
        ToolResultBlockParam result = second.get(2).content().asBlockParams().getFirst().asToolResult();
        assertThat(result.toolUseId()).isEqualTo("toolu_1");
        assertThat(result.content().orElseThrow().asString()).isEqualTo("{\"total\":1}");
        assertThat(result.isError()).isEmpty();
        // Las instrucciones incluyen las reglas configuradas y la fecha del reloj inyectado.
        String system = requests.getFirst().system().orElseThrow().asTextBlockParams().stream()
                .map(b -> b.text()).reduce("", String::concat);
        assertThat(system).contains("14 días", "máximo 3 préstamos", "15 de octubre de 2026");
    }

    /** Si una herramienta falla, Claude recibe el error marcado como tal y la conversación sigue. */
    @Test
    void toolFailureIsReportedToModelAsError() {
        when(api.create(any(MessageCreateParams.class))).thenReturn(
                toolUse("toolu_1", "buscar_prestamos", "{\"estado\":\"X\",\"texto\":\"\",\"pagina\":0}"),
                text("No he podido filtrar por ese estado."));
        when(tools.execute(anyString(), anyMap())).thenThrow(new BadRequestException("status no válido"));

        String reply = service.reply(List.of(user("Préstamos raros"))).reply();

        assertThat(reply).isEqualTo("No he podido filtrar por ese estado.");
        ToolResultBlockParam result = requests(2).get(1).messages().get(2)
                .content().asBlockParams().getFirst().asToolResult();
        assertThat(result.isError()).contains(true);
        assertThat(result.content().orElseThrow().asString()).contains("status no válido");
    }

    /** Un bucle de herramientas sin fin se corta tras el máximo de vueltas configurado. */
    @Test
    void stopsAfterMaxToolRounds() {
        when(api.create(any(MessageCreateParams.class)))
                .thenReturn(toolUse("toolu_x", "estadisticas", "{}"));
        when(tools.execute(anyString(), anyMap())).thenReturn("{}");

        String reply = service.reply(List.of(user("Hola"))).reply();

        assertThat(reply).isEqualTo(ChatService.TOO_MANY_STEPS_REPLY);
        verify(api, times(MAX_TOOL_ROUNDS + 1)).create(any(MessageCreateParams.class));
        verify(tools, times(MAX_TOOL_ROUNDS)).execute(anyString(), anyMap());
    }

    /** Si el modelo declina la petición, se responde con un mensaje fijo y sin herramientas. */
    @Test
    void refusalGetsFixedReply() {
        when(api.create(any(MessageCreateParams.class))).thenReturn(message("refusal", "[]"));

        assertThat(service.reply(List.of(user("…"))).reply()).isEqualTo(ChatService.REFUSAL_REPLY);
        verify(tools, never()).execute(anyString(), anyMap());
    }

    /**
     * Solo se envían los últimos mensajes (aquí 4) y nunca se empieza por uno del asistente:
     * de [u1, a1, u2, a2, u3] quedan [u2, a2, u3] tras descartar a1.
     */
    @Test
    void sendsOnlyRecentHistoryStartingWithUser() {
        when(api.create(any(MessageCreateParams.class))).thenReturn(text("Vale."));

        service.reply(List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2"), user("u3")));

        List<MessageParam> sent = requests(1).getFirst().messages();
        assertThat(sent).extracting(m -> m.content().asString()).containsExactly("u2", "a2", "u3");
        assertThat(sent.getFirst().role()).isEqualTo(MessageParam.Role.USER);
    }

    /** La conversación debe terminar en una pregunta del usuario. */
    @Test
    void rejectsConversationEndingWithAssistant() {
        assertThatThrownBy(() -> service.reply(List.of(user("Hola"), assistant("¿Qué necesitas?"))))
                .isInstanceOf(BadRequestException.class);
        verify(api, never()).create(any(MessageCreateParams.class));
    }

    /** Sin clave configurada no hay cliente: 503 con un mensaje que explica qué falta. */
    @Test
    void withoutClientIsUnavailable() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.reply(List.of(user("Hola"))))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }

    /** Un fallo de red con la API de Claude se convierte en 503, sin filtrar detalles internos. */
    @Test
    void connectionErrorIsUnavailable() {
        when(api.create(any(MessageCreateParams.class))).thenThrow(new AnthropicIoException("sin red"));

        assertThatThrownBy(() -> service.reply(List.of(user("Hola"))))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageNotContaining("sin red");
    }

    // --- utilidades ----------------------------------------------------------------------

    /** Peticiones enviadas a la API, comprobando que fueron exactamente {@code count}. */
    private List<MessageCreateParams> requests(int count) {
        ArgumentCaptor<MessageCreateParams> captor = ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(api, times(count)).create(captor.capture());
        return captor.getAllValues();
    }

    private static ChatMessage user(String text) {
        return new ChatMessage("user", text);
    }

    private static ChatMessage assistant(String text) {
        return new ChatMessage("assistant", text);
    }

    /** Respuesta final de Claude con un bloque de texto. */
    private static Message text(String text) {
        return message("end_turn", "[{\"type\":\"text\",\"text\":\"" + text + "\"}]");
    }

    /** Respuesta de Claude que pide ejecutar una herramienta. */
    private static Message toolUse(String id, String tool, String inputJson) {
        return message("tool_use", "[{\"type\":\"tool_use\",\"id\":\"" + id + "\",\"name\":\"" + tool
                + "\",\"input\":" + inputJson + "}]");
    }

    /** Construye un {@link Message} desde JSON con el mismo formato que devuelve la API. */
    private static Message message(String stopReason, String contentJson) {
        String json = """
                {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":%s,"stop_reason":"%s","stop_sequence":null,
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """.formatted(contentJson, stopReason);
        try {
            return ObjectMappers.jsonMapper().readValue(json, Message.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
