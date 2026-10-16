import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterRenderEffect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { ChatService, MAX_MESSAGE_LENGTH } from '../core/chat.service';
import { IconComponent } from '../shared/icon.component';

/** Preguntas de ejemplo que se ofrecen con la conversación vacía. */
export const SUGGESTIONS = [
  '¿Qué préstamos están vencidos?',
  '¿Qué libros de ciencia ficción hay disponibles?',
  '¿Quién tiene más préstamos activos?',
  '¿Cuánto dura un préstamo y cuándo se puede renovar?',
  '¿Qué libros tiene prestados Ana?',
  'Estadísticas de la biblioteca',
];

/**
 * Vista de la conversación: lista de mensajes y campo para escribir. Todo el estado vive en
 * {@link ChatService}; este componente solo lo muestra y le pasa lo que hace la persona.
 *
 * Las respuestas son texto plano y se muestran con interpolación normal (nunca como HTML), así
 * que un título de libro con etiquetas no puede inyectar nada en la página.
 */
@Component({
  selector: 'app-chat',
  imports: [IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div #log class="chat-log" role="log" aria-live="polite" aria-label="Conversación">
      <div class="chat-column">
        @if (!chat.hasMessages()) {
          <section class="welcome">
            <div class="welcome-icon"><app-icon name="sparkles" [size]="26" /></div>
            <h1>¿En qué te ayudo?</h1>
            <p>
              Pregunta por el catálogo, los usuarios o los préstamos de la biblioteca. Escribe
              «ayuda» para ver qué puedo responder. Solo consulto datos: los cambios se hacen desde
              la aplicación de la biblioteca.
            </p>
            <ul class="suggestions" aria-label="Preguntas de ejemplo">
              @for (s of suggestions; track s) {
                <li>
                  <button type="button" class="suggestion" (click)="chat.send(s)">{{ s }}</button>
                </li>
              }
            </ul>
          </section>
        }

        @for (m of chat.messages(); track $index) {
          <div class="message" [class.from-user]="m.role === 'user'">
            @if (m.role === 'assistant') {
              <div class="avatar" aria-hidden="true"><app-icon name="sparkles" [size]="14" /></div>
            }
            <div class="bubble">
              <span class="sr-only">{{ m.role === 'user' ? 'Tú:' : 'Asistente:' }}</span>
              {{ m.content }}
            </div>
          </div>
        }

        @if (chat.loading()) {
          <div class="message">
            <div class="avatar" aria-hidden="true"><app-icon name="sparkles" [size]="14" /></div>
            <div class="bubble typing" aria-label="El asistente está escribiendo">
              <span></span><span></span><span></span>
            </div>
          </div>
        }

        @if (chat.error(); as e) {
          <div class="chat-error" role="alert">
            <app-icon name="alert" [size]="16" />
            <span>{{ e }}</span>
            <button type="button" class="retry" (click)="chat.retry()">Reintentar</button>
          </div>
        }
      </div>
    </div>

    <form class="composer" (submit)="$event.preventDefault(); submit()">
      <div class="composer-inner">
        <label for="chat-input" class="sr-only">Escribe tu pregunta</label>
        <textarea
          #input
          id="chat-input"
          rows="1"
          placeholder="Escribe tu pregunta…"
          [maxLength]="maxLength"
          [value]="draft()"
          (input)="draft.set(input.value)"
          (keydown.enter)="onEnter($event)"
        ></textarea>
        <button
          type="submit"
          class="send-btn"
          [disabled]="chat.loading() || !draft().trim()"
          aria-label="Enviar pregunta"
        >
          <app-icon name="send" [size]="18" />
        </button>
      </div>
      <p class="hint">Enter para enviar · Mayús + Enter para un salto de línea</p>
    </form>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 0;
      flex: 1;
    }

    .chat-log {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem 1rem;
    }
    .chat-column {
      max-width: 760px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 0.9rem;
    }

    .welcome {
      text-align: center;
      padding: 2rem 0 1rem;
    }
    .welcome-icon {
      width: 56px;
      height: 56px;
      margin: 0 auto 1rem;
      border-radius: var(--radius-lg);
      background: var(--primary-light);
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .welcome h1 {
      margin: 0 0 0.5rem;
      font-size: 1.6rem;
      letter-spacing: -0.02em;
    }
    .welcome p {
      margin: 0 auto 1.5rem;
      max-width: 520px;
      color: var(--muted);
    }
    .suggestions {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 0.6rem;
    }
    .suggestion {
      width: 100%;
      height: 100%;
      text-align: left;
      padding: 0.8rem 1rem;
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      background: var(--surface);
      box-shadow: var(--shadow-sm);
      font-size: 0.9rem;
      cursor: pointer;
      transition: border-color 0.15s ease;
    }
    .suggestion:hover {
      border-color: var(--primary);
      color: var(--primary);
    }

    .message {
      display: flex;
      align-items: flex-end;
      gap: 0.5rem;
    }
    .message.from-user {
      justify-content: flex-end;
    }
    .avatar {
      width: 28px;
      height: 28px;
      flex-shrink: 0;
      border-radius: var(--radius-full);
      background: var(--primary-light);
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .bubble {
      max-width: min(85%, 620px);
      padding: 0.7rem 0.95rem;
      border-radius: var(--radius-lg);
      border-bottom-left-radius: var(--radius-xs);
      background: var(--surface);
      border: 1px solid var(--border);
      line-height: 1.55;
      /* Respeta los saltos de línea de las listas del asistente. */
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
    .from-user .bubble {
      background: var(--primary);
      border-color: var(--primary);
      color: var(--primary-text);
      border-radius: var(--radius-lg);
      border-bottom-right-radius: var(--radius-xs);
    }

    .typing {
      display: flex;
      gap: 0.3rem;
      padding: 0.85rem 1rem;
    }
    .typing span {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--muted);
      animation: blink 1.2s infinite ease-in-out;
    }
    .typing span:nth-child(2) {
      animation-delay: 0.15s;
    }
    .typing span:nth-child(3) {
      animation-delay: 0.3s;
    }
    @keyframes blink {
      0%,
      80%,
      100% {
        opacity: 0.3;
      }
      40% {
        opacity: 1;
      }
    }

    .chat-error {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.7rem 0.9rem;
      border-radius: var(--radius-md);
      background: var(--danger-light);
      border: 1px solid var(--danger-border);
      color: var(--danger);
      font-size: 0.9rem;
    }
    .chat-error span {
      flex: 1;
    }
    .retry {
      border: 0;
      background: transparent;
      font-weight: 700;
      text-decoration: underline;
      cursor: pointer;
      padding: 0.2rem 0.3rem;
    }

    .composer {
      border-top: 1px solid var(--border);
      background: var(--surface);
      padding: 0.85rem 1rem 0.6rem;
    }
    .composer-inner {
      max-width: 760px;
      margin: 0 auto;
      display: flex;
      align-items: flex-end;
      gap: 0.6rem;
    }
    textarea {
      flex: 1;
      resize: none;
      min-height: 46px;
      max-height: 160px;
      padding: 0.7rem 0.9rem;
      border: 1px solid var(--border-strong);
      border-radius: var(--radius-md);
      background: var(--bg);
      color: var(--text);
      font: inherit;
      field-sizing: content;
    }
    textarea:focus {
      outline: none;
      border-color: var(--primary);
      box-shadow: 0 0 0 3px var(--primary-light);
    }
    .send-btn {
      width: 46px;
      height: 46px;
      flex-shrink: 0;
      border: 0;
      border-radius: var(--radius-md);
      background: var(--primary);
      color: var(--primary-text);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .send-btn:hover:not(:disabled) {
      background: var(--primary-hover);
    }
    .send-btn:disabled {
      opacity: 0.45;
      cursor: default;
    }
    .hint {
      max-width: 760px;
      margin: 0.4rem auto 0;
      font-size: 0.75rem;
      color: var(--muted);
    }

    @media (max-width: 640px) {
      .welcome h1 {
        font-size: 1.35rem;
      }
      .suggestions {
        grid-template-columns: 1fr;
      }
      .hint {
        display: none;
      }
    }
  `,
})
export class ChatComponent {
  protected readonly chat = inject(ChatService);
  protected readonly suggestions = SUGGESTIONS;
  protected readonly maxLength = MAX_MESSAGE_LENGTH;

  /** Texto que se está escribiendo (estado de la vista, no de la conversación). */
  readonly draft = signal('');

  private readonly log = viewChild.required<ElementRef<HTMLElement>>('log');
  private readonly input = viewChild.required<ElementRef<HTMLTextAreaElement>>('input');

  constructor() {
    // Tras pintar un mensaje nuevo (o el indicador de escritura) se baja hasta el final.
    afterRenderEffect(() => {
      this.chat.messages();
      this.chat.loading();
      this.chat.error();
      const log = this.log().nativeElement;
      log.scrollTop = log.scrollHeight;
    });
  }

  /** Pone el foco en el campo de texto (al empezar una conversación nueva). */
  focusInput(): void {
    this.input().nativeElement.focus();
  }

  /** Enter envía; Mayús+Enter añade un salto de línea. */
  onEnter(event: Event): void {
    if ((event as KeyboardEvent).shiftKey) return;
    event.preventDefault();
    this.submit();
  }

  /** Envía lo escrito y vacía el campo si se aceptó. */
  submit(): void {
    if (this.chat.send(this.draft())) {
      this.draft.set('');
    }
  }
}
