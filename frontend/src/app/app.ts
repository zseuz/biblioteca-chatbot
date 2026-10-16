import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  viewChild,
} from '@angular/core';
import { ChatComponent } from './chat/chat.component';
import { ChatService } from './core/chat.service';
import { ThemeService } from './core/theme.service';
import { IconComponent } from './shared/icon.component';

/**
 * Componente raíz: cabecera (nombre, estado del catálogo, nueva conversación y tema), avisos de
 * conexión y la vista del chat ocupando el resto de la pantalla.
 */
@Component({
  selector: 'app-root',
  imports: [ChatComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="masthead">
      <div class="brand">
        <span class="seal" aria-hidden="true"><app-icon name="book-open" [size]="18" /></span>
        <div>
          <span class="title">Biblioteca</span>
          <span class="label subtitle">Asistente de consultas</span>
        </div>
      </div>

      <div class="actions">
        <span class="label status" [attr.data-state]="connection().state" role="status">
          <i aria-hidden="true"></i>{{ connection().text }}
        </span>
        @if (chat.hasMessages()) {
          <button
            type="button"
            class="icon-btn"
            (click)="newConversation()"
            [disabled]="chat.loading()"
            aria-label="Nueva conversación"
            title="Nueva conversación"
          >
            <app-icon name="refresh" [size]="17" />
          </button>
        }
        <button
          type="button"
          class="icon-btn"
          (click)="theme.toggle()"
          [attr.aria-label]="
            theme.theme() === 'dark' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'
          "
          [title]="theme.theme() === 'dark' ? 'Modo claro' : 'Modo oscuro'"
        >
          <app-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" [size]="17" />
        </button>
      </div>
    </header>

    <!-- Avisos de conexión: se muestran antes de que la persona pregunte. -->
    @if (chat.offline()) {
      <p class="status-banner danger" role="alert">
        <app-icon name="alert" [size]="16" />
        No se pudo conectar con el servidor del asistente (puerto 8081). Arráncalo y recarga la
        página.
      </p>
    } @else if (chat.status()?.bibliotecaAvailable === false) {
      <p class="status-banner warn" role="alert">
        <app-icon name="alert" [size]="16" />
        La API de la biblioteca no responde (puerto 8080). Arranca su backend para consultar datos.
      </p>
    }

    <app-chat />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      height: 100dvh;
    }

    .masthead {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.8rem 1.5rem;
      border-bottom: 3px double var(--rule-strong);
      background: var(--paper);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      min-width: 0;
    }
    /* Sello de lacre con el libro. */
    .seal {
      width: 38px;
      height: 38px;
      flex-shrink: 0;
      display: grid;
      place-items: center;
      border-radius: 50%;
      background: var(--accent);
      color: var(--accent-ink);
      box-shadow:
        inset 0 0 0 2px var(--accent),
        inset 0 0 0 3px var(--accent-ink);
    }
    .title {
      display: block;
      font-family: var(--font-display);
      font-size: 1.45rem;
      font-weight: 600;
      font-style: italic;
      line-height: 1;
      letter-spacing: -0.015em;
    }
    .subtitle {
      display: block;
      margin-top: 0.25rem;
    }

    .actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .status {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      margin-right: 0.4rem;
    }
    .status i {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--ink-faint);
    }
    .status[data-state='online'] i {
      background: var(--ok);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--ok) 25%, transparent);
    }
    .status[data-state='degraded'] i {
      background: var(--warn);
    }
    .status[data-state='offline'] i {
      background: var(--danger);
    }
    .icon-btn {
      width: 38px;
      height: 38px;
      padding: 0;
      display: grid;
      place-items: center;
      border: 1px solid var(--rule-strong);
      border-radius: var(--radius);
      background: var(--card);
      cursor: pointer;
      transition:
        border-color 0.15s ease,
        color 0.15s ease;
    }
    .icon-btn:hover:not(:disabled) {
      border-color: var(--accent);
      color: var(--accent);
    }
    .icon-btn:disabled {
      opacity: 0.4;
      cursor: default;
    }

    .status-banner {
      margin: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.6rem 1.25rem;
      font-size: 0.92rem;
      text-align: center;
      border-bottom: 1px solid currentColor;
    }
    .status-banner.warn {
      background: var(--warn-wash);
      color: var(--warn);
    }
    .status-banner.danger {
      background: var(--danger-wash);
      color: var(--danger);
    }

    @media (max-width: 640px) {
      .masthead {
        padding: 0.65rem 1rem;
      }
      .subtitle {
        display: none;
      }
      .status {
        font-size: 0;
        margin-right: 0;
      }
    }
  `,
})
export class App implements OnInit {
  protected readonly chat = inject(ChatService);
  protected readonly theme = inject(ThemeService);

  private readonly chatView = viewChild.required(ChatComponent);

  /** Estado de la conexión para el indicador de la cabecera. */
  protected readonly connection = computed(() => {
    if (this.chat.offline()) return { state: 'offline', text: 'Sin servidor' };
    const status = this.chat.status();
    if (!status) return { state: 'unknown', text: 'Conectando…' };
    return status.bibliotecaAvailable
      ? { state: 'online', text: 'Catálogo en línea' }
      : { state: 'degraded', text: 'Catálogo sin conexión' };
  });

  ngOnInit(): void {
    this.chat.refreshStatus();
  }

  /** Vacía la conversación y deja el cursor listo para escribir. */
  protected newConversation(): void {
    this.chat.reset();
    this.chatView().focusInput();
  }
}
