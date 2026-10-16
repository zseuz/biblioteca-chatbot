import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChatMessage, ChatReply, ChatStatus } from './models';

/** Dirección del backend del asistente (no la de la biblioteca: esa la consulta el backend). */
export const API_URL = 'http://localhost:8081/api';

/** Convierte un error HTTP en un mensaje legible (el backend lo envía en el campo message). */
export function messageFor(err: HttpErrorResponse): string {
  if (err.status === 0) {
    return 'No se pudo conectar con el servidor del asistente. ¿Está encendido (puerto 8081)?';
  }
  const body = err.error as { message?: string } | null;
  return body?.message ?? `Error inesperado (${err.status})`;
}

/** Único lugar del frontend que habla con el backend del asistente. */
@Injectable({ providedIn: 'root' })
export class ChatApiService {
  private readonly http = inject(HttpClient);

  /** POST /chat: envía la conversación (el último mensaje, del usuario) y recibe la respuesta. */
  send(messages: ChatMessage[]): Observable<ChatReply> {
    return this.http.post<ChatReply>(`${API_URL}/chat`, { messages });
  }

  /** GET /chat/status: si la API de la biblioteca responde. */
  status(): Observable<ChatStatus> {
    return this.http.get<ChatStatus>(`${API_URL}/chat/status`);
  }
}
