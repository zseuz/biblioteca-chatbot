package com.biblioteca.chatbot.controller;

import com.biblioteca.chatbot.model.ChatRequest;
import com.biblioteca.chatbot.model.ChatResponse;
import com.biblioteca.chatbot.model.ChatStatus;
import com.biblioteca.chatbot.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador del chat: traduce HTTP ↔ modelo y delega toda la lógica en {@link ChatService}.
 * La vista (el frontend Angular) solo habla con estos dos endpoints.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** Servicio que conversa con Claude y consulta la biblioteca. */
    private final ChatService service;

    /** Spring inyecta el servicio por constructor. */
    public ChatController(ChatService service) {
        this.service = service;
    }

    /**
     * POST /api/chat → respuesta del asistente al último mensaje de la conversación.
     * 400 si la conversación no es válida; 503 si Claude no está configurado o no responde.
     */
    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return service.reply(request.messages());
    }

    /** GET /api/chat/status → si hay clave de Claude y si la API de la biblioteca responde. */
    @GetMapping("/status")
    public ChatStatus status() {
        return service.status();
    }
}
