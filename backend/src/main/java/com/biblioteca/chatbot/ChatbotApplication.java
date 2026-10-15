package com.biblioteca.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Asistente de la biblioteca: API (Spring MVC) que responde preguntas en lenguaje natural
 * consultando la API del Sistema de Biblioteca con ayuda de Claude.
 *
 * <p>Organización MVC:
 * <ul>
 *   <li><b>Modelo</b> ({@code model}): mensajes, peticiones y respuestas del chat.</li>
 *   <li><b>Vista</b>: el frontend Angular ({@code ../frontend}), que muestra la conversación.</li>
 *   <li><b>Controlador</b> ({@code controller}): recibe las peticiones HTTP y delega en los
 *       servicios ({@code service}), que hablan con Claude y con la biblioteca ({@code client}).</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatbotApplication {

    /** Arranca el servidor en el puerto 8081 (configurable con {@code SERVER_PORT}). */
    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
