package com.biblioteca.chatbot.client;

import com.biblioteca.chatbot.config.BibliotecaProperties;
import com.biblioteca.chatbot.model.Book;
import com.biblioteca.chatbot.model.LoanPage;
import com.biblioteca.chatbot.model.Member;
import com.biblioteca.chatbot.model.Stats;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de solo lectura de la API REST del Sistema de Biblioteca. Cada método corresponde a un
 * endpoint {@code GET} y devuelve los datos ya convertidos a las clases del modelo.
 *
 * <p>Los valores de búsqueda van como variables de plantilla ({@code {q}}): RestClient los
 * codifica, así que un texto con {@code &} o espacios no altera la URL.
 */
@Component
public class BibliotecaApiClient {

    private static final Logger log = LoggerFactory.getLogger(BibliotecaApiClient.class);

    /** Cliente HTTP con la URL base y los tiempos de espera configurados (ver {@code AppConfig}). */
    private final RestClient http;
    /** Convierte el JSON de las respuestas en objetos del modelo. */
    private final JsonMapper json;
    /** URL base, solo para los mensajes de error. */
    private final String baseUrl;

    /** Inyección por constructor. */
    public BibliotecaApiClient(RestClient bibliotecaRestClient, JsonMapper json, BibliotecaProperties properties) {
        this.http = bibliotecaRestClient;
        this.json = json;
        this.baseUrl = properties.baseUrl();
    }

    /** GET /books?q= → libros con el texto en título, autor o género (sin tildes; vacío = todos). */
    public List<Book> books(String query) {
        return get(new TypeReference<>() { }, "/books?q={q}", query);
    }

    /** GET /loans → una página del historial de préstamos, del más reciente al más antiguo. */
    public LoanPage loans(String status, String query, int size) {
        return get(new TypeReference<>() { }, "/loans?status={status}&q={q}&page=0&size={size}", status, query, size);
    }

    /** GET /members → todos los usuarios con sus contadores de préstamos. */
    public List<Member> members() {
        return get(new TypeReference<>() { }, "/members");
    }

    /** GET /stats → indicadores y rankings del panel de estadísticas. */
    public Stats stats() {
        return get(new TypeReference<>() { }, "/stats");
    }

    /** {@code true} si la API de la biblioteca responde (se usa para el aviso de estado). */
    public boolean isAvailable() {
        try {
            get(new TypeReference<JsonNode>() { }, "/loans/summary");
            return true;
        } catch (BibliotecaApiException e) {
            return false;
        }
    }

    /**
     * Hace la petición y convierte los fallos en {@link BibliotecaApiException} con un mensaje
     * útil: el de la propia API si respondió con error, o uno de conexión si no respondió.
     */
    private <T> T get(TypeReference<T> type, String uri, Object... variables) {
        try {
            String body = http.get().uri(uri, variables).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                throw new BibliotecaApiException("La API de la biblioteca devolvió una respuesta vacía");
            }
            return json.readValue(body, type);
        } catch (RestClientResponseException e) {
            String message = apiMessage(e.getResponseBodyAsString());
            log.warn("La API de la biblioteca respondió {} en {}: {}", e.getStatusCode().value(), uri, message);
            throw new BibliotecaApiException("La biblioteca rechazó la consulta (HTTP "
                    + e.getStatusCode().value() + "): " + message);
        } catch (ResourceAccessException e) {
            log.warn("No se pudo conectar con la API de la biblioteca en {}: {}", baseUrl, e.getMessage());
            throw new BibliotecaApiException("No se pudo conectar con la API de la biblioteca (" + baseUrl
                    + "). Comprueba que su backend está en marcha.");
        } catch (JacksonException e) {
            throw new BibliotecaApiException("La API de la biblioteca devolvió una respuesta con un formato inesperado");
        }
    }

    /** Extrae el campo {@code message} del error de la biblioteca ({@code ApiError}), si existe. */
    private String apiMessage(String body) {
        try {
            JsonNode message = json.readTree(body).get("message");
            return message != null && message.isString() ? message.asString() : "sin detalle";
        } catch (JacksonException e) {
            return "sin detalle";
        }
    }
}
