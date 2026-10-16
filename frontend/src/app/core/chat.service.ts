import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { ChatApiService, messageFor } from './chat-api.service';
import { ChatMessage, ChatStatus } from './models';

/** Longitud máxima de un mensaje (la misma que valida el backend). */
export const MAX_MESSAGE_LENGTH = 2000;

/**
 * Estado de la conversación y acciones sobre ella. Los componentes solo leen estas signals y
 * llaman a los métodos; no hacen peticiones HTTP ni guardan estado propio.
 */
@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly api = inject(ChatApiService);

  /** Conversación en orden cronológico. */
  readonly messages = signal<ChatMessage[]>([]);
  /** Esperando la respuesta del asistente. */
  readonly loading = signal(false);
  /** Error de la última pregunta (se muestra con opción de reintentar). */
  readonly error = signal<string | null>(null);
  /** Estado del servidor; null mientras no se conoce (o si el servidor no responde). */
  readonly status = signal<ChatStatus | null>(null);
  /** El servidor del asistente no respondió al pedir el estado. */
  readonly offline = signal(false);
  /** Hay conversación que se pueda reiniciar. */
  readonly hasMessages = computed(() => this.messages().length > 0);

  /** Pide al servidor si la API de la biblioteca responde. */
  refreshStatus(): void {
    this.api.status().subscribe({
      next: (s) => {
        this.status.set(s);
        this.offline.set(false);
      },
      error: () => {
        this.status.set(null);
        this.offline.set(true);
      },
    });
  }

  /** Añade la pregunta a la conversación y pide la respuesta. Ignora textos vacíos. */
  send(text: string): boolean {
    const content = text.trim().slice(0, MAX_MESSAGE_LENGTH);
    if (!content || this.loading()) return false;
    this.messages.update((m) => [...m, { role: 'user', content }]);
    this.ask();
    return true;
  }

  /** Vuelve a pedir la respuesta a la última pregunta tras un error. */
  retry(): void {
    if (!this.loading() && this.error()) this.ask();
  }

  /** Empieza una conversación nueva. */
  reset(): void {
    if (this.loading()) return;
    this.messages.set([]);
    this.error.set(null);
  }

  /** Envía la conversación al backend y añade la respuesta (o guarda el error). */
  private ask(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.send(this.messages()).subscribe({
      next: ({ reply }) => {
        this.messages.update((m) => [...m, { role: 'assistant', content: reply }]);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(messageFor(err));
        this.loading.set(false);
        // Si falló por configuración o conexión, el aviso de estado se actualiza solo.
        this.refreshStatus();
      },
    });
  }
}
