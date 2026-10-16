package com.biblioteca.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Asistente de la biblioteca: API (Spring MVC) que responde preguntas en lenguaje natural
 * consultando la API del Sistema de Biblioteca. No usa IA: interpreta cada pregunta con reglas
 * de palabras clave, así que funciona sin coste ni conexión a servicios externos.
 *
 * <p>Organización MVC:
 * <ul>
 *   <li><b>Modelo</b> ({@code model}): datos de la biblioteca ({@code Book}, {@code Loan},
 *       {@code Member}, {@code Stats}), la pregunta interpretada ({@code Question}, {@code Intent})
 *       y los mensajes del chat.</li>
 *   <li><b>Vista</b>: el frontend Angular ({@code ../frontend}), que muestra la conversación.</li>
 *   <li><b>Controlador</b> ({@code controller}): recibe las peticiones HTTP y delega en los
 *       servicios ({@code service}), que interpretan la pregunta y consultan la biblioteca
 *       ({@code client}).</li>
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
