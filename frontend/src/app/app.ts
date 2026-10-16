import { ChangeDetectionStrategy, Component, OnInit, inject, viewChild } from '@angular/core';
import { ChatComponent } from './chat/chat.component';
import { ChatService } from './core/chat.service';
import { ThemeService } from './core/theme.service';
import { IconComponent } from './shared/icon.component';

/**
 * Componente raíz: cabecera (título, estado del servidor, nueva conversación y tema) y la
 * vista del chat ocupando el resto de la pantalla.
 */
@Component({
  selector: 'app-root',
  imports: [ChatComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="app-header">
      <div class="brand">
        <div class="brand-logo"><app-icon name="book-open" [size]="18" /></div>
        <div>
          <span class="brand-title">Asistente de la Biblioteca</span>
          <span class="brand-sub">Consulta libros, usuarios y préstamos</span>
        </div>
      </div>
      <div class="actions">
        @if (chat.hasMessages()) {
          <button
            type="button"
            class="icon-btn"
            (click)="newConversation()"
            [disabled]="chat.loading()"
            aria-label="Nueva conversación"
            title="Nueva conversación"
          >
            <app-icon name="refresh" [size]="18" />
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
          <app-icon [name]="theme.theme() === 'dark' ? 'sun' : 'moon'" [size]="18" />
        </button>
      </div>
    </header>

    <!-- Avisos de configuración: se muestran antes de que la persona pregunte. -->
    @if (chat.offline()) {
      <p class="status-banner danger" role="status">
        <app-icon name="alert" [size]="16" />
        No se pudo conectar con el servidor del asistente (puerto 8081). Arráncalo y recarga la
        página.
      </p>
    } @else if (chat.status()?.bibliotecaAvailable === false) {
      <p class="status-banner warn" role="status">
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

    .app-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.75rem 1.25rem;
      background: var(--surface);
      border-bottom: 1px solid var(--border);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 0.7rem;
      min-width: 0;
    }
    .brand-logo {
      width: 36px;
      height: 36px;
      flex-shrink: 0;
      border-radius: var(--radius-md);
      background: linear-gradient(135deg, #4f46e5 0%, #6366f1 100%);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .brand-title {
      display: block;
      font-weight: 800;
      letter-spacing: -0.01em;
    }
    .brand-sub {
      display: block;
      font-size: 0.78rem;
      color: var(--muted);
    }
    .actions {
      display: flex;
      gap: 0.4rem;
    }
    .icon-btn {
      width: 38px;
      height: 38px;
      padding: 0;
      border: 1px solid var(--border);
      border-radius: var(--radius-md);
      background: var(--surface);
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
    }
    .icon-btn:hover:not(:disabled) {
      background: var(--surface-subtle);
      border-color: var(--border-strong);
    }
    .icon-btn:disabled {
      opacity: 0.5;
      cursor: default;
    }

    .status-banner {
      margin: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.55rem 1rem;
      font-size: 0.85rem;
      text-align: center;
    }
    .status-banner.warn {
      background: var(--warn-light);
      color: var(--warn);
    }
    .status-banner.danger {
      background: var(--danger-light);
      color: var(--danger);
    }

    @media (max-width: 640px) {
      .app-header {
        padding: 0.65rem 1rem;
      }
      .brand-sub {
        display: none;
      }
    }
  `,
})
export class App implements OnInit {
  protected readonly chat = inject(ChatService);
  protected readonly theme = inject(ThemeService);

  private readonly chatView = viewChild.required(ChatComponent);

  ngOnInit(): void {
    this.chat.refreshStatus();
  }

  /** Vacía la conversación y deja el cursor listo para escribir. */
  protected newConversation(): void {
    this.chat.reset();
    this.chatView().focusInput();
  }
}
