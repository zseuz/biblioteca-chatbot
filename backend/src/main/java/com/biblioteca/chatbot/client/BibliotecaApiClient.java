package com.biblioteca.chatbot.client;

import com.biblioteca.chatbot.config.BibliotecaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente de solo lectura de la API REST del Sistema de Biblioteca. Cada método corresponde a un
 * endpoint {@code GET} y devuelve el JSON tal cual (el asistente lo lee directamente).
 *
 * <p>Los valores de búsqueda van como variables de plantilla ({@code {q}}): RestClient los
 * codifica, así que un texto con {@code &} o espacios no altera la URL.
 */
@Component
public class BibliotecaApiClient {

    private static final Logger log = LoggerFactory.getLogger(BibliotecaApiClient.class);

    /** Cliente HTTP con la URL base y los tiempos de espera configurados (ver {@code AppConfig}). */
    private final RestClient http;
    /** Lee el JSON de las respuestas. */
    private final JsonMapper json;
    /** URL base, solo para los mensajes de error. */
    private final String baseUrl;

    /** Inyección por constructor. */
    public BibliotecaApiClient(RestClient bibliotecaRestClient, JsonMapper json, BibliotecaProperties properties) {
        this.http = bibliotecaRestClient;
        this.json = json;
        this.baseUrl = properties.baseUrl();
    }

    /** GET /books?q= → libros que contienen el texto en título, autor o género (vacío = todos). */
    public JsonNode books(String query) {
        return get("/books?q={q}", query);
    }

    /** GET /loans → una página del historial de préstamos, del más reciente al más antiguo. */
    public JsonNode loans(String status, String query, int page, int size) {
        return get("/loans?status={status}&q={q}&page={page}&size={size}", status, query, page, size);
    }

    /** GET /members → todos los usuarios con sus contadores de préstamos. */
    public JsonNode members() {
        return get("/members");
    }

    /** GET /stats → indicadores y rankings del panel de estadísticas. */
    public JsonNode stats() {
        return get("/stats");
    }

    /** {@code true} si la API de la biblioteca responde (se usa para el aviso de estado). */
    public boolean isAvailable() {
        try {
            get("/loans/summary");
            return true;
        } catch (BibliotecaApiException e) {
            return false;
        }
    }

    /**
     * Hace la petición y convierte los fallos en {@link BibliotecaApiException} con un mensaje
     * útil: el de la propia API si respondió con error, o uno de conexión si no respondió.
     */
    private JsonNode get(String uri, Object... variables) {
        try {
            String body = http.get().uri(uri, variables).retrieve().body(String.class);
            return json.readTree(body == null ? "null" : body);
        } catch (RestClientResponseException e) {
            String message = apiMessage(e.getResponseBodyAsString());
            log.warn("La API de la biblioteca respondió {} en {}: {}", e.getStatusCode().value(), uri, message);
            throw new BibliotecaApiException("La biblioteca rechazó la consulta (HTTP "
                    + e.getStatusCode().value() + "): " + message);
        } catch (ResourceAccessException e) {
            log.warn("No se pudo conectar con la API de la biblioteca en {}: {}", baseUrl, e.getMessage());
            throw new BibliotecaApiException("No se pudo conectar con la API de la biblioteca (" + baseUrl
                    + "). Comprueba que el backend de la biblioteca está en marcha.");
        } catch (JacksonException e) {
            throw new BibliotecaApiException("La API de la biblioteca devolvió una respuesta que no es JSON válido");
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
