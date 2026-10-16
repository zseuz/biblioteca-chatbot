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
import { ReplyBlocksPipe } from './reply-blocks.pipe';

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
 * Vista de la conversación: bienvenida con preguntas de ejemplo, mensajes y campo para escribir.
 * Todo el estado vive en {@link ChatService}; este componente solo lo muestra y le pasa lo que
 * hace la persona.
 *
 * Las respuestas son texto plano: {@link ReplyBlocksPipe} las trocea en párrafos y listas, y se
 * muestran con interpolación normal (nunca como HTML), así que un título de libro con etiquetas
 * no puede inyectar nada en la página.
 */
@Component({
  selector: 'app-chat',
  imports: [IconComponent, ReplyBlocksPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div #log class="chat-log" role="log" aria-live="polite" aria-label="Conversación">
      <div class="column">
        @if (!chat.hasMessages()) {
          <section class="welcome">
            <p class="label">Mostrador de consultas · Sala general</p>
            <h1>¿Qué buscamos <em>hoy</em>?</h1>
            <p class="lede">
              Pregunta por el catálogo, los usuarios o los préstamos. Respondo con los datos de la
              biblioteca al momento; escribe «ayuda» para ver todo lo que entiendo.
            </p>
            <ul class="cards" aria-label="Preguntas de ejemplo">
              @for (s of suggestions; track s; let i = $index) {
                <li [style.--i]="i">
                  <button type="button" class="suggestion" (click)="chat.send(s)">
                    <span class="call-number" aria-hidden="true">C·0{{ i + 1 }}</span>
                    <span class="question">{{ s }}</span>
                    <span class="go" aria-hidden="true"
                      ><app-icon name="arrow-right" [size]="16"
                    /></span>
                  </button>
                </li>
              }
            </ul>
          </section>
        }

        @for (m of chat.messages(); track $index) {
          <article class="message" [class.from-user]="m.role === 'user'">
            <p class="label who">{{ m.role === 'user' ? 'Tú' : 'Asistente' }}</p>
            <div class="bubble">
              @if (m.role === 'user') {
                <p>{{ m.content }}</p>
              } @else {
                @for (b of m.content | replyBlocks; track $index) {
                  @if (b.kind === 'list') {
                    <ul>
                      @for (item of b.items; track $index) {
                        <li>{{ item }}</li>
                      }
                    </ul>
                  } @else {
                    <p>{{ b.text }}</p>
                  }
                }
              }
            </div>
          </article>
        }

        @if (chat.loading()) {
          <article class="message">
            <p class="label who">Asistente</p>
            <div class="bubble typing" aria-label="El asistente está consultando el catálogo">
              <span class="label">Consultando el catálogo</span>
              <span class="dots" aria-hidden="true"><i></i><i></i><i></i></span>
            </div>
          </article>
        }

        @if (chat.error(); as e) {
          <div class="chat-error" role="alert">
            <app-icon name="alert" [size]="18" />
            <div>
              <p class="label">No se pudo responder</p>
              <p>{{ e }}</p>
            </div>
            <button type="button" class="retry" (click)="chat.retry()">Reintentar</button>
          </div>
        }
      </div>
    </div>

    <form class="composer" (submit)="$event.preventDefault(); submit()">
      <div class="column composer-row">
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
          title="Enviar"
        >
          <app-icon name="arrow-up" [size]="20" />
        </button>
      </div>
      <p class="column hint label">
        <span>Enter envía · Mayús + Enter, nueva línea</span>
        @if (draft().length > maxLength * 0.8) {
          <span>{{ draft().length }}/{{ maxLength }}</span>
        }
      </p>
    </form>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 0;
      flex: 1;
    }
    .column {
      width: 100%;
      max-width: 760px;
      margin: 0 auto;
    }
    .chat-log {
      flex: 1;
      overflow-y: auto;
      padding: 2rem 1.25rem 1.5rem;
      scroll-behavior: smooth;
    }
    .chat-log > .column {
      display: flex;
      flex-direction: column;
      gap: 1.4rem;
    }

    /* --- Bienvenida -------------------------------------------------------------------- */
    .welcome {
      padding: 2.5rem 0 1rem;
      animation: rise 0.5s ease both;
    }
    .welcome h1 {
      margin: 0.6rem 0 0.75rem;
      font-family: var(--font-display);
      font-weight: 500;
      font-size: clamp(2.3rem, 6vw, 3.6rem);
      line-height: 1.02;
      letter-spacing: -0.025em;
      font-variation-settings: 'opsz' 144;
    }
    .welcome h1 em {
      color: var(--accent);
      font-weight: 400;
    }
    .lede {
      margin: 0 0 2rem;
      max-width: 34rem;
      color: var(--ink-soft);
      font-size: 1.08rem;
    }
    .cards {
      list-style: none;
      margin: 0;
      padding: 0;
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 0.85rem;
    }
    .cards li {
      animation: rise 0.45s ease both;
      animation-delay: calc(0.12s + var(--i) * 0.05s);
    }

    /* Ficha de catálogo: signatura arriba, pregunta y el agujero del fichero abajo. */
    .suggestion {
      position: relative;
      width: 100%;
      height: 100%;
      min-height: 6.2rem;
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.45rem;
      padding: 0.85rem 1rem 1.35rem;
      text-align: left;
      background: var(--card);
      border: 1px solid var(--rule);
      border-top: 3px solid var(--rule-strong);
      border-radius: var(--radius);
      box-shadow: var(--shadow-card);
      cursor: pointer;
      transition:
        transform 0.18s ease,
        border-color 0.18s ease;
    }
    .suggestion::after {
      content: '';
      position: absolute;
      bottom: 0.45rem;
      left: 50%;
      width: 9px;
      height: 9px;
      margin-left: -4.5px;
      border-radius: 50%;
      background: var(--paper);
      box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.25);
    }
    .suggestion:hover {
      transform: translateY(-3px) rotate(-0.4deg);
      border-top-color: var(--accent);
    }
    .call-number {
      font-family: var(--font-mono);
      font-size: 0.7rem;
      letter-spacing: 0.1em;
      color: var(--accent);
    }
    .question {
      font-size: 1.04rem;
      line-height: 1.35;
      padding-right: 1.5rem;
    }
    .go {
      position: absolute;
      right: 0.85rem;
      top: 0.8rem;
      color: var(--accent);
      opacity: 0;
      transform: translateX(-4px);
      transition: 0.18s ease;
    }
    .suggestion:hover .go,
    .suggestion:focus-visible .go {
      opacity: 1;
      transform: none;
    }

    /* --- Mensajes ---------------------------------------------------------------------- */
    .message {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 0.3rem;
      animation: rise 0.3s ease both;
    }
    .message.from-user {
      align-items: flex-end;
    }
    .who {
      margin: 0;
    }
    .bubble {
      max-width: min(88%, 640px);
      padding: 0.85rem 1.1rem;
      background: var(--card);
      border: 1px solid var(--rule);
      border-left: 3px solid var(--accent);
      border-radius: var(--radius);
      box-shadow: var(--shadow-card);
      overflow-wrap: anywhere;
    }
    .bubble p {
      margin: 0;
      white-space: pre-line;
    }
    .bubble p + p,
    .bubble ul + p,
    .bubble p + ul {
      margin-top: 0.55rem;
    }
    .bubble ul {
      margin: 0;
      padding: 0;
      list-style: none;
    }
    .bubble li {
      position: relative;
      padding: 0.32rem 0 0.32rem 1.1rem;
      border-bottom: 1px dashed var(--rule);
    }
    .bubble li:last-child {
      border-bottom: 0;
    }
    .bubble li::before {
      content: '';
      position: absolute;
      left: 0.1rem;
      top: 0.95em;
      width: 5px;
      height: 5px;
      background: var(--accent);
      transform: rotate(45deg);
    }
    .from-user .bubble {
      background: var(--user-bg);
      color: var(--user-ink);
      border-color: var(--user-bg);
      border-left-width: 1px;
      font-style: italic;
    }

    .typing {
      display: flex;
      align-items: center;
      gap: 0.6rem;
    }
    .dots {
      display: inline-flex;
      gap: 4px;
    }
    .dots i {
      width: 5px;
      height: 5px;
      background: var(--accent);
      transform: rotate(45deg);
      animation: blink 1.1s infinite ease-in-out;
    }
    .dots i:nth-child(2) {
      animation-delay: 0.15s;
    }
    .dots i:nth-child(3) {
      animation-delay: 0.3s;
    }

    .chat-error {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.85rem 1rem;
      background: var(--danger-wash);
      border: 1px solid var(--danger);
      border-left-width: 3px;
      border-radius: var(--radius);
      color: var(--danger);
    }
    .chat-error > div {
      flex: 1;
    }
    .chat-error p {
      margin: 0;
    }
    .chat-error .label {
      color: inherit;
    }
    .retry {
      padding: 0.35rem 0.75rem;
      border: 1px solid currentColor;
      border-radius: var(--radius);
      background: transparent;
      font-family: var(--font-mono);
      font-size: 0.72rem;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      cursor: pointer;
    }
    .retry:hover {
      background: var(--danger);
      color: var(--card);
    }

    /* --- Campo de escritura ------------------------------------------------------------ */
    .composer {
      padding: 0.9rem 1.25rem 0.7rem;
      background: var(--paper-deep);
      border-top: 3px double var(--rule-strong);
    }
    .composer-row {
      display: flex;
      align-items: flex-end;
      gap: 0.6rem;
    }
    textarea {
      flex: 1;
      resize: none;
      min-height: 50px;
      max-height: 170px;
      padding: 0.75rem 0.95rem;
      border: 1px solid var(--rule-strong);
      border-radius: var(--radius);
      background: var(--card);
      color: var(--ink);
      font: inherit;
      field-sizing: content;
    }
    textarea::placeholder {
      color: var(--ink-faint);
      font-style: italic;
    }
    textarea:focus {
      outline: none;
      border-color: var(--accent);
      box-shadow: 0 0 0 3px var(--accent-wash);
    }
    .send-btn {
      width: 50px;
      height: 50px;
      flex-shrink: 0;
      border: 0;
      border-radius: var(--radius);
      background: var(--accent);
      color: var(--accent-ink);
      display: grid;
      place-items: center;
      cursor: pointer;
      transition:
        background 0.15s ease,
        transform 0.15s ease;
    }
    .send-btn:hover:not(:disabled) {
      background: var(--accent-hover);
      transform: translateY(-1px);
    }
    .send-btn:disabled {
      opacity: 0.35;
      cursor: default;
    }
    .hint {
      display: flex;
      justify-content: space-between;
      margin-top: 0.45rem;
      margin-bottom: 0;
      font-size: 0.62rem;
    }

    @keyframes rise {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
    }
    @keyframes blink {
      0%,
      80%,
      100% {
        opacity: 0.25;
      }
      40% {
        opacity: 1;
      }
    }

    @media (max-width: 640px) {
      .chat-log {
        padding: 1.25rem 1rem;
      }
      .welcome {
        padding-top: 1rem;
      }
      .cards {
        grid-template-columns: 1fr;
      }
      .suggestion {
        min-height: 0;
      }
      .bubble {
        max-width: 94%;
      }
      .composer {
        padding: 0.7rem 1rem;
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
    // Tras pintar un mensaje nuevo (o el indicador de escritura) se baja hasta el final. Con la
    // conversación vacía se queda arriba, para que la bienvenida se lea desde el principio.
    afterRenderEffect(() => {
      const hasMessages = this.chat.hasMessages();
      this.chat.messages();
      this.chat.loading();
      this.chat.error();
      const log = this.log().nativeElement;
      log.scrollTop = hasMessages ? log.scrollHeight : 0;
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

  /**
   * Envía lo escrito y vacía el campo si se aceptó. Se lee y se vacía también el propio
   * elemento: si se escribe (o pega) y se pulsa Enter antes de que Angular repinte, el enlace
   * [value] no detecta el cambio y el texto se quedaría en el campo.
   */
  submit(): void {
    const field = this.input().nativeElement;
    if (this.chat.send(field.value || this.draft())) {
      field.value = '';
      this.draft.set('');
    }
  }
}
