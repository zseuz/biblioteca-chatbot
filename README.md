# Asistente de la Biblioteca

Chatbot que responde preguntas escritas en español sobre el **Sistema de Biblioteca**: *«¿qué préstamos están vencidos?»*, *«¿quedan ejemplares de Dune?»*, *«¿qué libros tiene prestados Ana?»*. Consulta los datos reales a través de la API REST de la biblioteca.

**No usa IA ni servicios de pago:** interpreta cada pregunta con reglas de palabras clave, así que es gratis, responde al instante y funciona sin internet. **Solo consulta**: no presta, devuelve ni modifica nada.

| Capa | Tecnología | Puerto |
|---|---|---|
| Frontend (vista) | Angular 22 | 4201 |
| Backend (modelo y controladores) | Spring Boot 4.1 (Spring MVC), Java 21 | 8081 |
| Datos | API del proyecto `biblioteca` (el chatbot no tiene base de datos propia) | 8080 |

---

## Qué se le puede preguntar

| Pregunta (ejemplos) | Qué responde |
|---|---|
| *¿Qué préstamos están vencidos?* · *Préstamos atrasados de María* | Préstamos vencidos con los días de retraso, de todos o de un usuario o libro |
| *¿Hay ejemplares de Dune?* · *¿Está disponible 1984?* | Disponibilidad de un libro (ejemplares libres de los totales) |
| *¿Qué libros de ciencia ficción hay disponibles?* · *Libros de García Márquez* | Búsqueda por título, autor o género, con disponibilidad |
| *¿Qué libros tiene prestados Ana?* · *¿Cuántos préstamos tiene Luis?* | Libros sin devolver de un usuario (marcando los vencidos) y su total histórico |
| *¿Quién tiene Cien años de soledad?* | Quién tiene prestado ahora ese libro y hasta cuándo |
| *¿Quién tiene más préstamos activos?* · *¿Quién tiene más préstamos?* | Ranking de usuarios por préstamos actuales o totales |
| *¿Cuáles son los libros más prestados?* · *Géneros más populares* | Rankings del panel de estadísticas |
| *Estadísticas* · *¿Cuántos libros hay?* | Totales de libros, usuarios y préstamos |
| *Préstamos en plazo* · *¿Qué libros hay disponibles?* · *Lista de usuarios* | Listados |
| *¿Cuánto dura un préstamo?* · *¿Cuándo se puede renovar?* | Reglas de préstamo |
| *Presta Dune a Ana* | Explica que solo consulta y en qué pantalla de la biblioteca hacerlo |
| *Hola* · *ayuda* | Saludo y lista de ejemplos |

No distingue mayúsculas ni tildes («garcia marquez» encuentra «García Márquez»). Si una pregunta no encaja en ninguna regla, el texto se busca como título, autor, género o nombre de usuario. Si tampoco hay coincidencias, el asistente lo dice y sugiere cómo preguntar.

---

## Diseño de la interfaz

Estilo **«mostrador de consultas»** de una biblioteca clásica, en lugar del chat genérico:

- **Tipografía:** Fraunces para los títulos, Newsreader (serif de lectura) para los mensajes e IBM Plex Mono para las etiquetas, como en una ficha de catálogo.
- **Color:** papel, tinta y acento rojo lacre, con un grano de papel muy sutil. El **tema oscuro** («sala de lectura») usa tonos nogal y acento cobre. Se elige con el botón de la cabecera y se recuerda en el navegador; la primera vez sigue la preferencia del sistema.
- **Preguntas de ejemplo** como fichas de catálogo con su signatura (C·01…).
- **Respuestas** con las listas formateadas: las líneas «- …» del backend se muestran como lista, siempre como texto y nunca como HTML.
- Indicador de **estado del catálogo** en la cabecera, animaciones suaves (desactivadas si el sistema pide reducir movimiento) y diseño adaptado a móvil.

Los colores y las fuentes están definidos como variables en `frontend/src/styles.css`.

---

## Arquitectura MVC

```text
 Navegador                 biblioteca-chatbot                               biblioteca
┌────────────────┐  POST  ┌────────────────────────────────────────────┐ GET  ┌──────────────┐
│ VISTA          │ /api/  │ CONTROLADOR  ChatController                │/api/ │ API REST     │
│ Angular 4201   │ ─────► │      │                                     │────► │ Spring 8080  │
│ app.ts         │  chat  │      ▼                                     │books │ (libros,     │
│ chat.component │ ◄───── │ SERVICIOS    IntentClassifier → Question   │loans │  usuarios,   │
│                │  reply │              ChatService (redacta)         │members│ préstamos)   │
│ ChatService    │        │      │                                     │stats │              │
│ (estado)       │        │ CLIENTE      BibliotecaApiClient ──────────┼────► │              │
└────────────────┘        │ MODELO       Book, Loan, Member, Stats,    │      └──────────────┘
                          │              Intent, Question, ChatMessage…│
                          └────────────────────────────────────────────┘
```

- **Modelo** (`backend/.../model`): los datos de la biblioteca (`Book`, `Loan`, `LoanPage`, `Member`, `Stats`), la pregunta interpretada (`Intent`, `Question`) y los mensajes del chat (`ChatMessage`, `ChatRequest`, `ChatResponse`, `ChatStatus`). En el frontend, las formas del chat están en `core/models.ts`.
- **Vista** (`frontend`): Angular muestra la conversación. `ChatService` guarda el estado (mensajes, carga, error) y los componentes `App` y `ChatComponent` solo lo pintan y le pasan lo que hace la persona.
- **Controlador** (`controller/ChatController`): recibe las peticiones HTTP, valida el modelo y delega en los servicios.
- **Servicios** (`service`): `IntentClassifier` interpreta la pregunta; `ChatService` consulta la biblioteca con `BibliotecaApiClient` (`client`) y redacta la respuesta.

### Cómo se interpreta una pregunta

1. **Normalizar:** minúsculas, sin tildes ni signos. *«¿Qué libros tiene prestados Ana?»* → `que libros tiene prestados ana`.
2. **Intención:** se comprueban reglas de palabras clave de la más concreta a la más general: saludo, ayuda, reglas, acciones, vencidos, rankings, estadísticas, préstamos de alguien, búsqueda…
3. **Sujeto:** se quitan las palabras de relleno (*qué, hay, libros, préstamos, de…*) del principio y del final, y lo que queda es el nombre, título, autor o género (*Ana*). Solo se recortan los extremos, para no romper títulos como *Cien años **de** soledad*.
4. **Responder:** `ChatService` hace las consultas necesarias a la API de la biblioteca y redacta el texto. Cuando hay un nombre, primero se comprueba si es un usuario y después si es un libro, o al revés según la pregunta.

Para añadir un tipo de pregunta nuevo: añade un valor a `Intent`, una regla en `IntentClassifier.classify` y su respuesta en `ChatService.reply`, con un caso en `IntentClassifierTest` y otro en `ChatServiceTest`.

---

## Requisitos

- **JDK 21 o superior** (`java -version`).
- **Node 22.22+ LTS o 24.15+** (`node -v`).
- El proyecto **`biblioteca`** en marcha (su backend en el puerto 8080).

---

## Ponerlo en marcha

Necesitas tres terminales.

**Terminal 1 — API de la biblioteca** (desde la carpeta `biblioteca/backend`):

```bash
.\mvnw.cmd spring-boot:run
```

**Terminal 2 — backend del asistente** (desde `biblioteca-chatbot/backend`):

| Sistema | Comando |
|---|---|
| Windows (PowerShell) | `.\mvnw.cmd spring-boot:run` |
| Windows (CMD) | `mvnw.cmd spring-boot:run` |
| macOS / Linux | `./mvnw spring-boot:run` |

Está listo cuando muestra `Tomcat started on port 8081`.

**Terminal 3 — frontend** (desde `biblioteca-chatbot/frontend`):

```bash
npm install
```

```bash
npm start
```

Abre **http://localhost:4201**. El indicador de la cabecera muestra si el catálogo está en línea; si la biblioteca o el servidor del asistente no responden, aparece además un aviso.

---

## API

URL base: `http://localhost:8081/api`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/chat` | `{messages: [{role: "user" \| "assistant", content}]}` → `{reply}`. Se responde al último mensaje, que debe ser del usuario (máximo 2000 caracteres) | 200 · 400 · 503 |
| `GET` | `/chat/status` | `{bibliotecaAvailable}` | 200 |

Los errores usan el mismo formato que la biblioteca: `{timestamp, status, message, path, fields?}`. `503` significa que la API de la biblioteca no responde o rechazó la consulta. Las preguntas que no necesitan datos, como las reglas o la ayuda, se responden aunque la biblioteca esté apagada.

---

## Configuración

`backend/src/main/resources/application.properties`, sobrescribible con variables de entorno:

| Propiedad | Por defecto | Variable | Descripción |
|---|---|---|---|
| `server.port` | `8081` | `SERVER_PORT` | Puerto del backend del asistente |
| `biblioteca.base-url` | `http://localhost:8080/api` | `BIBLIOTECA_API_URL` | API de la biblioteca |
| `biblioteca.timeout` | `10s` | `BIBLIOTECA_TIMEOUT` | Espera máxima por consulta |
| `biblioteca.loan-days` / `max-active-loans` / `renewal-window-days` | `14` / `3` / `5` | `BIBLIOTECA_LOAN_DAYS`… | Reglas de préstamo que explica el asistente. Deben coincidir con `library.loans.*` de la biblioteca (su API no las publica) |
| `app.cors.allowed-origins` | `http://localhost:4201` | `CORS_ALLOWED_ORIGINS` | Origen del frontend |

La URL del backend que usa el frontend está en `frontend/src/app/core/chat-api.service.ts` (`API_URL`).

---

## Pruebas

| Proyecto | Comando | Qué cubre |
|---|---|---|
| Backend (65 tests) | `cd backend` y `.\mvnw.cmd test` | Interpretación de 32 preguntas de ejemplo (intención y sujeto), respuestas con la biblioteca simulada (vencidos, disponibilidad, préstamos de un usuario, quién tiene un libro, rankings, listas largas, usuarios ambiguos), cliente contra un servidor simulado (URLs, JSON, errores) y endpoints (validación, 503, estado, CORS) |
| Frontend (16 tests) | `cd frontend` y `npm test -- --watch=false` | Estado de la conversación, errores y reintento, indicador y aviso de conexión, sugerencias, Enter / Mayús+Enter (también enviando antes de que la vista se repinte), respuestas con listas pero sin interpretar HTML, troceado de respuestas en párrafos y listas, y nueva conversación |

Las pruebas **nunca** llaman a la biblioteca real.

---

## Estructura

```text
biblioteca-chatbot/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/biblioteca/chatbot/
│       ├── ChatbotApplication.java
│       ├── model/          Book, Loan, LoanPage, Member, Stats, Intent, Question, ChatMessage…
│       ├── controller/     ChatController
│       ├── service/        IntentClassifier (interpreta), ChatService (responde)
│       ├── client/         BibliotecaApiClient (API de la biblioteca)
│       ├── config/         AppConfig, BibliotecaProperties
│       └── exception/      Errores y manejador global
└── frontend/
    └── src/app/
        ├── app.ts                  Cabecera, aviso de estado y tema
        ├── chat/                   Conversación y campo de texto; reply-blocks.pipe (párrafos y listas)
        ├── core/                   Modelos, ChatApiService (HTTP), ChatService (estado), tema
        └── shared/icon.component.ts
```

---

## Solución de problemas

| Síntoma | Solución |
|---|---|
| Aviso *"La API de la biblioteca no responde"* | Arranca el backend de `biblioteca` (terminal 1) |
| Aviso *"No se pudo conectar con el servidor del asistente"* | Arranca el backend del asistente (terminal 2) y recarga |
| Sigue apareciendo un mensaje sobre `ANTHROPIC_API_KEY` | Está corriendo una versión anterior del backend del asistente: detenla (`Ctrl + C`) y arráncala de nuevo |
| `Port 8081 was already in use` | Ya hay otra instancia del asistente abierta: ciérrala, o usa `SERVER_PORT=8082` (y cambia `API_URL` en el frontend) |
| El asistente no entiende una pregunta | Escribe «ayuda» para ver ejemplos, o pregunta solo por el título, autor o nombre |
