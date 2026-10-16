package com.biblioteca.chatbot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoints con la aplicación completa arrancada y la biblioteca apuntando a un puerto donde no
 * hay nada (ver {@code src/test/resources/application.properties}).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTest {

    @Autowired
    private MockMvc mvc;

    /** Si la biblioteca no responde: 503 con un mensaje que explica qué arrancar. */
    @Test
    void unreachableBibliotecaRespondsServiceUnavailable() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"¿Hay libros de Dune?\"}]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(containsString("No se pudo conectar con la API de la biblioteca")));
    }

    /** Las preguntas que no necesitan datos se responden aunque la biblioteca esté apagada. */
    @Test
    void answersRulesWithoutBiblioteca() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"messages\":[{\"role\":\"user\",\"content\":\"¿Cuánto dura un préstamo?\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(containsString("14 días")));
    }

    /** Conversación vacía, rol desconocido, mensaje en blanco o que no termina en el usuario → 400. */
    @Test
    void validatesRequest() throws Exception {
        for (String body : new String[] {
                "{\"messages\":[]}",
                "{\"messages\":[{\"role\":\"system\",\"content\":\"hola\"}]}",
                "{\"messages\":[{\"role\":\"user\",\"content\":\"  \"}]}",
                "no es json"}) {
            mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    /** El estado indica que la biblioteca no responde. */
    @Test
    void statusReportsUnreachableBiblioteca() throws Exception {
        mvc.perform(get("/api/chat/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bibliotecaAvailable").value(false));
    }

    /** CORS: el frontend en el puerto 4201 puede llamar a la API. */
    @Test
    void allowsFrontendOrigin() throws Exception {
        mvc.perform(options("/api/chat")
                        .header("Origin", "http://localhost:4201")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4201"));
    }
}
