import { Injectable, signal } from '@angular/core';

/** Tema de color de la interfaz. */
export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'chatbot-theme';

/**
 * Tema claro/oscuro: empieza con el del sistema operativo (o el último elegido) y se aplica
 * con el atributo data-theme en <html>, que activa las variables de styles.css.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.initial());

  constructor() {
    this.apply(this.theme());
  }

  /** Cambia entre claro y oscuro y recuerda la elección en este navegador. */
  toggle(): void {
    const next: Theme = this.theme() === 'dark' ? 'light' : 'dark';
    this.theme.set(next);
    this.apply(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // Sin almacenamiento (modo privado): el tema se mantiene solo en esta visita.
    }
  }

  private initial(): Theme {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved === 'light' || saved === 'dark') return saved;
    } catch {
      // Sin almacenamiento: se usa la preferencia del sistema.
    }
    return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches
      ? 'dark'
      : 'light';
  }

  private apply(theme: Theme): void {
    document.documentElement.setAttribute('data-theme', theme);
  }
}
