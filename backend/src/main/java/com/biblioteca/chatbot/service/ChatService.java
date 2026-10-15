package com.biblioteca.chatbot.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import com.biblioteca.chatbot.client.BibliotecaApiClient;
import com.biblioteca.chatbot.config.BibliotecaProperties;
import com.biblioteca.chatbot.config.ChatProperties;
import com.biblioteca.chatbot.exception.BadRequestException;
import com.biblioteca.chatbot.exception.ServiceUnavailableException;
import com.biblioteca.chatbot.model.ChatMessage;
import com.biblioteca.chatbot.model.ChatResponse;
import com.biblioteca.chatbot.model.ChatStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Responde preguntas en lenguaje natural consultando la biblioteca con las herramientas de
 * {@link LibraryTools}.
 *
 * <p>Bucle de herramientas: se envía la conversación a Claude; si responde pidiendo una
 * herramienta ({@code stop_reason = tool_use}) se ejecuta contra la API de la biblioteca, se le
 * devuelve el resultado y se vuelve a preguntar, hasta que contesta con texto o se alcanza
 * {@code chat.max-tool-rounds}.
 *
 * <p>No guarda estado entre peticiones (el cliente reenvía el historial) ni modifica datos.
 */
@Service
public class ChatService {

    /** Registro de errores (nunca se registra el contenido de las preguntas). */
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /** Tope de la respuesta del modelo (incluido su razonamiento interno). */
    private static final long MAX_TOKENS = 16_000L;
    /** Respuesta cuando el modelo declina una petición (stop_reason = refusal). */
    static final String REFUSAL_REPLY =
            "No puedo ayudar con esa petición. Puedo responder preguntas sobre libros, usuarios y préstamos.";
    /** Respuesta cuando la pregunta requiere más consultas de las permitidas. */
    static final String TOO_MANY_STEPS_REPLY =
            "La pregunta necesitaba demasiadas consultas. Prueba a hacerla más concreta.";
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"));

    /** Cliente de Claude; solo existe si hay clave configurada (ver {@code AppConfig}). */
    private final ObjectProvider<AnthropicClient> claude;
    private final LibraryTools tools;
    private final BibliotecaApiClient biblioteca;
    private final ChatProperties chat;
    /** Reglas de préstamo: se explican al modelo para preguntas como «¿cuánto dura un préstamo?». */
    private final BibliotecaProperties rules;
    /** Reloj inyectado: el modelo necesita saber qué día es para hablar de vencimientos. */
    private final Clock clock;

    /** Inyección por constructor (el cliente de Claude es opcional: sin clave se responde 503). */
    public ChatService(ObjectProvider<AnthropicClient> claude, LibraryTools tools, BibliotecaApiClient biblioteca,
                       ChatProperties chat, BibliotecaProperties rules, Clock clock) {
        this.claude = claude;
        this.tools = tools;
        this.biblioteca = biblioteca;
        this.chat = chat;
        this.rules = rules;
        this.clock = clock;
    }

    /** Estado actual: si hay clave de Claude y si la biblioteca responde. */
    public ChatStatus status() {
        return new ChatStatus(claude.getIfAvailable() != null, biblioteca.isAvailable());
    }

    /**
     * Responde al último mensaje de la conversación.
     *
     * @throws BadRequestException         si el último mensaje no es del usuario
     * @throws ServiceUnavailableException si falta la clave o la API de Claude falla
     */
    public ChatResponse reply(List<ChatMessage> conversation) {
        AnthropicClient api = claude.getIfAvailable();
        if (api == null) {
            throw new ServiceUnavailableException(
                    "El asistente no está configurado: falta la clave de la API de Claude (ANTHROPIC_API_KEY).");
        }
        List<MessageParam> messages = history(conversation);

        for (int round = 0; ; round++) {
            Message response = call(api, messages);
            // Se devuelve la respuesta completa (no solo su texto): así conserva su razonamiento
            // y las peticiones de herramienta, que la API exige junto a sus resultados.
            messages.add(response.toParam());
            StopReason stop = response.stopReason().orElse(StopReason.END_TURN);

            if (StopReason.TOOL_USE.equals(stop)) {
                if (round >= chat.maxToolRounds()) {
                    return new ChatResponse(TOO_MANY_STEPS_REPLY);
                }
                messages.add(runTools(response));
                continue;
            }
            if (StopReason.REFUSAL.equals(stop)) {
                return new ChatResponse(REFUSAL_REPLY);
            }
            String text = text(response);
            return new ChatResponse(text.isBlank() ? "No he podido generar una respuesta. Inténtalo de nuevo." : text);
        }
    }

    /**
     * Convierte el historial del cliente en mensajes de la API: solo los más recientes, y
     * empezando siempre por un mensaje del usuario (la API no admite empezar con el asistente).
     */
    private List<MessageParam> history(List<ChatMessage> conversation) {
        if (conversation.isEmpty() || !conversation.getLast().fromUser()) {
            throw new BadRequestException("El último mensaje de la conversación debe ser del usuario");
        }
        List<ChatMessage> recent = conversation.subList(
                Math.max(0, conversation.size() - chat.maxHistory()), conversation.size());
        List<MessageParam> messages = new ArrayList<>();
        for (ChatMessage m : recent) {
            if (messages.isEmpty() && !m.fromUser()) {
                continue;
            }
            messages.add(MessageParam.builder()
                    .role(m.fromUser() ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                    .content(m.content())
                    .build());
        }
        return messages;
    }

    /**
     * Llama a Claude y traduce sus errores a un 503 con un mensaje para el usuario. Del más
     * concreto al más general: los límites de uso y la clave inválida tienen su propio mensaje.
     */
    private Message call(AnthropicClient api, List<MessageParam> messages) {
        try {
            return api.messages().create(params(messages));
        } catch (RateLimitException e) {
            log.warn("Límite de uso de la API de Claude alcanzado");
            throw new ServiceUnavailableException("El asistente está recibiendo muchas preguntas. Espera unos segundos.");
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.error("La API de Claude rechazó la clave configurada (HTTP {})", e.statusCode());
            throw new ServiceUnavailableException("La clave de la API de Claude no es válida. Revisa ANTHROPIC_API_KEY.");
        } catch (AnthropicServiceException e) {
            log.error("Error de la API de Claude (HTTP {}): {}", e.statusCode(), e.getMessage());
            throw unavailable();
        } catch (AnthropicIoException e) {
            log.error("No se pudo conectar con la API de Claude: {}", e.getMessage());
            throw unavailable();
        } catch (AnthropicException e) {
            log.error("Error inesperado del cliente de Claude", e);
            throw unavailable();
        }
    }

    private static ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException("El asistente no está disponible en este momento. Inténtalo más tarde.");
    }

    /**
     * Petición a Claude. Lo fijo (herramientas e instrucciones) va primero y se marca para la
     * caché de prompts; la fecha de hoy va después, para no invalidar esa caché.
     */
    private MessageCreateParams params(List<MessageParam> messages) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(chat.model())
                .maxTokens(MAX_TOKENS)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(instructions())
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build(),
                        TextBlockParam.builder()
                                .text("Hoy es " + LocalDate.now(clock).format(DATE) + ".")
                                .build()))
                // Esfuerzo bajo: son consultas de chat, prima la rapidez de respuesta.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                // Si los filtros de seguridad del modelo rechazan una pregunta legítima, la API la
                // reintenta automáticamente con otro modelo adecuado.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .messages(messages);
        tools.definitions().forEach(builder::addTool);
        return builder.build();
    }

    /** Instrucciones del asistente: qué es, qué puede hacer y cómo debe responder. */
    private String instructions() {
        return """
                Eres el asistente del Sistema de Biblioteca. Ayudas al personal a consultar el catálogo, \
                los usuarios y los préstamos.

                Usa las herramientas para obtener los datos: no inventes libros, usuarios, fechas ni cifras. \
                Si las herramientas no devuelven lo que se pregunta, dilo claramente. Si una herramienta \
                indica que la biblioteca no responde, explícalo y sugiere comprobar que su servidor está en \
                marcha. Puedes llamar a varias herramientas a la vez si la pregunta lo requiere.

                Solo puedes consultar, no modificar. Si te piden prestar, devolver, renovar, dar de alta o \
                eliminar algo, explica que debe hacerse desde la aplicación de la biblioteca (pantallas \
                Libros, Usuarios o Préstamos).

                Reglas de la biblioteca:
                - Cada préstamo dura %d días.
                - Un usuario puede tener como máximo %d préstamos activos a la vez.
                - Un usuario con préstamos vencidos no puede pedir más libros hasta devolverlos.
                - Solo se presta un libro si tiene ejemplares disponibles.
                - Un préstamo se puede renovar cuando faltan %d días o menos para su vencimiento y no \
                está vencido; la renovación da de nuevo el plazo completo desde ese día.

                Forma de responder:
                - En español, breve y directo.
                - Texto plano: la respuesta se muestra tal cual, sin interpretar Markdown (nada de \
                asteriscos, almohadillas ni tablas). Para listas, una línea por elemento empezando por "- ".
                - Fechas en formato día/mes/año (por ejemplo 15/10/2026).
                - Los estados de préstamo se llaman "en plazo" (ACTIVE), "vencido" (OVERDUE) y "devuelto" (RETURNED).
                """.formatted(rules.loanDays(), rules.maxActiveLoans(), rules.renewalWindowDays());
    }

    /**
     * Ejecuta todas las herramientas pedidas en la respuesta y devuelve sus resultados en un
     * único mensaje (la API espera todos los resultados juntos). Un fallo se devuelve como error
     * para que el modelo lo explique, sin cortar la conversación.
     */
    private MessageParam runTools(Message response) {
        List<ContentBlockParam> results = new ArrayList<>();
        for (ContentBlock block : response.content()) {
            block.toolUse().ifPresent(call -> results.add(ContentBlockParam.ofToolResult(runTool(call))));
        }
        return MessageParam.builder()
                .role(MessageParam.Role.USER)
                .contentOfBlockParams(results)
                .build();
    }

    /** Ejecuta una herramienta y empaqueta su resultado (o su error) para el modelo. */
    @SuppressWarnings("unchecked")
    private ToolResultBlockParam runTool(ToolUseBlock call) {
        ToolResultBlockParam.Builder result = ToolResultBlockParam.builder().toolUseId(call.id());
        try {
            Map<String, Object> input = call._input().convert(Map.class);
            return result.content(tools.execute(call.name(), input == null ? Map.of() : input)).build();
        } catch (RuntimeException e) {
            log.debug("Herramienta {} falló: {}", call.name(), e.getMessage());
            return result.content("Error: " + e.getMessage()).isError(true).build();
        }
    }

    /** Une los bloques de texto de la respuesta. */
    private static String text(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text().strip())
                .filter(t -> !t.isEmpty())
                .collect(Collectors.joining("\n\n"));
    }
}
