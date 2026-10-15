# Asistente de la Biblioteca

Chatbot que responde preguntas en lenguaje natural sobre el **Sistema de Biblioteca** (*«¿qué préstamos están vencidos?»*, *«¿quedan ejemplares de Dune?»*, *«¿quién tiene más préstamos activos?»*). Usa Claude (API de Anthropic) y consulta los datos reales a través de la API REST de la biblioteca. **Solo consulta**: no presta, devuelve ni modifica nada.

| Capa | Tecnología | Puerto |
|---|---|---|
| Frontend (vista) | Angular 22 | 4201 |
| Backend (modelo y controladores) | Spring Boot 4.1 (Spring MVC), Java 21, SDK de Anthropic | 8081 |
| Datos | API del proyecto `biblioteca` (no tiene base de datos propia) | 8080 |

---

## Arquitectura MVC

```text
 Navegador                 biblioteca-chatbot                          biblioteca
┌───────────────┐  POST   ┌───────────────────────────────────────┐   GET  ┌──────────────┐
│ VISTA         │ /api/   │ CONTROLADOR  ChatController           │ /api/  │ API REST     │
│ Angular 4201  │ ──────► │      │                                │ ─────► │ Spring 8080  │
│ app.ts        │  chat   │      ▼                                │ books  │ (libros,     │
│ chat.component│ ◄────── │ SERVICIO     ChatService ◄──► Claude  │ loans  │  usuarios,   │
│               │  reply  │      │       (bucle de herramientas)  │ members│  préstamos)  │
│ ChatService   │         │      ▼                                │ stats  │              │
│ (estado)      │         │              LibraryTools             │        │              │
└───────────────┘         │              BibliotecaApiClient ─────┼───────►│              │
                          │ MODELO       ChatMessage, ChatRequest,│        └──────────────┘
                          │              ChatResponse, ChatStatus │
                          └───────────────────────────────────────┘
```

- **Modelo** (`backend/.../model`): los datos que viajan entre vista y controlador (`ChatMessage`, `ChatRequest`, `ChatResponse`, `ChatStatus`). En el frontend, las mismas formas están en `core/models.ts`.
- **Vista** (`frontend`): Angular muestra la conversación. `ChatService` guarda el estado (mensajes, carga, error) y los componentes `App` y `ChatComponent` solo lo pintan y le pasan lo que hace la persona.
- **Controlador** (`backend/.../controller/ChatController`): recibe las peticiones HTTP, valida el modelo y delega en los servicios.
- **Servicios** (`service`): `ChatService` conversa con Claude; cuando Claude necesita datos pide una herramienta (`LibraryTools`), que los obtiene de la biblioteca con `BibliotecaApiClient` (`client`).

### Cómo se responde una pregunta

1. La vista envía toda la conversación a `POST /api/chat` (el servidor no guarda estado).
2. `ChatService` se la pasa a Claude junto con las herramientas disponibles.
3. Si Claude pide una herramienta (p. ej. `buscar_prestamos` con estado `OVERDUE`), se llama a la API de la biblioteca y se le devuelve el resultado. Esto se repite hasta que Claude responde con texto (máximo `chat.max-tool-rounds` consultas).
4. La respuesta, en texto plano, vuelve a la vista.

| Herramienta | Llama a | Para |
|---|---|---|
| `buscar_libros` | `GET /api/books?q=` | Catálogo y ejemplares disponibles |
| `buscar_prestamos` | `GET /api/loans?status=&q=&page=&size=20` | Historial por estado (en plazo, vencido, devuelto) |
| `buscar_usuarios` | `GET /api/members` (filtrado en el chatbot) | Usuarios y sus préstamos activos / totales |
| `estadisticas` | `GET /api/stats` | Totales y rankings |

---

## Requisitos

- **JDK 21 o superior** (`java -version`).
- **Node 22.22+ LTS o 24.15+** (`node -v`).
- El proyecto **`biblioteca`** en marcha (su backend en el puerto 8080).
- Una **clave de la API de Claude** ([console.anthropic.com](https://console.anthropic.com/)). Sin ella todo arranca, pero el asistente responde que no está configurado.

---

## Ponerlo en marcha

Necesitas tres terminales.

**Terminal 1 — API de la biblioteca** (desde la carpeta `biblioteca/backend`):

```bash
.\mvnw.cmd spring-boot:run
```

**Terminal 2 — backend del asistente** (desde `biblioteca-chatbot/backend`). Define primero la clave en la misma terminal:

| Sistema | Clave | Arrancar |
|---|---|---|
| Windows (PowerShell) | `$env:ANTHROPIC_API_KEY="tu-clave"` | `.\mvnw.cmd spring-boot:run` |
| Windows (CMD) | `set ANTHROPIC_API_KEY=tu-clave` | `mvnw.cmd spring-boot:run` |
| macOS / Linux | `export ANTHROPIC_API_KEY=tu-clave` | `./mvnw spring-boot:run` |

Está listo cuando muestra `Tomcat started on port 8081`.

**Terminal 3 — frontend** (desde `biblioteca-chatbot/frontend`):

```bash
npm install
```

```bash
npm start
```

Abre **http://localhost:4201**. Si falta algo (la clave, la biblioteca o el propio servidor del asistente), la página lo indica con un aviso arriba.

> Cada pregunta consume tokens de tu cuenta de Anthropic. La clave solo la usa el servidor: nunca llega al navegador. No la guardes en `application.properties` ni la subas a un repositorio.

---

## API

URL base: `http://localhost:8081/api`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/chat` | `{messages: [{role: "user" \| "assistant", content}]}` (el último, del usuario; máximo 2000 caracteres por mensaje) → `{reply}` | 200 · 400 · 503 |
| `GET` | `/chat/status` | `{claudeConfigured, bibliotecaAvailable}` | 200 |

Los errores usan el mismo formato que la biblioteca: `{timestamp, status, message, path, fields?}`. `503` significa que falta la clave o que la API de Claude no responde. Si la **biblioteca** no responde, el asistente lo explica en su respuesta.

---

## Configuración

`backend/src/main/resources/application.properties`, sobrescribible con variables de entorno:

| Propiedad | Por defecto | Variable | Descripción |
|---|---|---|---|
| `server.port` | `8081` | `SERVER_PORT` | Puerto del backend del asistente |
| `chat.api-key` | *(vacía)* | `ANTHROPIC_API_KEY` | Clave de la API de Claude |
| `chat.model` | `claude-opus-5-5` | `CHAT_MODEL` | Modelo de Claude |
| `chat.max-tool-rounds` | `6` | `CHAT_MAX_TOOL_ROUNDS` | Consultas a la biblioteca por respuesta |
| `chat.max-history` | `20` | `CHAT_MAX_HISTORY` | Mensajes recientes que se envían como contexto |
| `biblioteca.base-url` | `http://localhost:8080/api` | `BIBLIOTECA_API_URL` | API de la biblioteca |
| `biblioteca.timeout` | `10s` | `BIBLIOTECA_TIMEOUT` | Espera máxima por consulta |
| `biblioteca.loan-days` / `max-active-loans` / `renewal-window-days` | `14` / `3` / `5` | `BIBLIOTECA_LOAN_DAYS`… | Reglas de préstamo que explica el asistente. Deben coincidir con `library.loans.*` de la biblioteca (su API no las publica) |
| `app.cors.allowed-origins` | `http://localhost:4201` | `CORS_ALLOWED_ORIGINS` | Origen del frontend |

La URL del backend que usa el frontend está en `frontend/src/app/core/chat-api.service.ts` (`API_URL`).

---

## Pruebas

| Proyecto | Comando | Qué cubre |
|---|---|---|
| Backend (22 tests) | `cd backend` y `.\mvnw.cmd test` | Bucle de herramientas con respuestas de Claude simuladas (herramienta → resultado → respuesta, errores, tope de vueltas, rechazos, historial), herramientas contra una API de biblioteca simulada (URLs, codificación, recorte a 20, filtro de usuarios sin tildes, errores) y endpoints (validación, 503 sin clave, estado, CORS) |
| Frontend (11 tests) | `cd frontend` y `npm test -- --watch=false` | Estado de la conversación, errores y reintento, avisos de configuración, sugerencias, Enter / Mayús+Enter, respuesta como texto plano y nueva conversación |

Las pruebas **nunca** llaman a Claude ni a la biblioteca reales.

---

## Estructura

```text
biblioteca-chatbot/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/biblioteca/chatbot/
│       ├── ChatbotApplication.java
│       ├── model/          ChatMessage, ChatRequest, ChatResponse, ChatStatus
│       ├── controller/     ChatController
│       ├── service/        ChatService (Claude), LibraryTools (herramientas)
│       ├── client/         BibliotecaApiClient (API de la biblioteca)
│       ├── config/         AppConfig, ChatProperties, BibliotecaProperties
│       └── exception/      Errores y manejador global
└── frontend/
    └── src/app/
        ├── app.ts                  Cabecera, avisos de estado y tema
        ├── chat/chat.component.ts  Conversación y campo de texto
        ├── core/                   Modelos, ChatApiService (HTTP), ChatService (estado), tema
        └── shared/icon.component.ts
```

---

## Solución de problemas

| Síntoma | Solución |
|---|---|
| Aviso *"Falta la clave de Claude"* | Define `ANTHROPIC_API_KEY` en la terminal 2 y reinicia el backend del asistente |
| Aviso *"La API de la biblioteca no responde"* | Arranca el backend de `biblioteca` (terminal 1) |
| Aviso *"No se pudo conectar con el servidor del asistente"* | Arranca el backend del asistente (terminal 2) y recarga |
| *"La clave de la API de Claude no es válida"* | Revisa la clave y el saldo en console.anthropic.com |
| `Port 8081 was already in use` | Cierra la otra instancia o usa `SERVER_PORT=8082` (y cambia `API_URL` en el frontend) |
