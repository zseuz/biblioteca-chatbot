/**
 * Modelos: la forma de los datos que intercambian la vista y la API del asistente
 * (mismos nombres de campo que los records Java del paquete model del backend).
 */

/** Un mensaje de la conversación. */
export interface ChatMessage {
  /** Quién lo escribió: la persona ('user') o el asistente ('assistant'). */
  role: 'user' | 'assistant';
  /** Texto plano del mensaje. */
  content: string;
}

/** Respuesta de POST /api/chat. */
export interface ChatReply {
  /** Texto plano (sin Markdown) listo para mostrar. */
  reply: string;
}

/** Respuesta de GET /api/chat/status. */
export interface ChatStatus {
  /** La API de la biblioteca responde. */
  bibliotecaAvailable: boolean;
}
