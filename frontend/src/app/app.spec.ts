import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { SUGGESTIONS } from './chat/chat.component';
import { API_URL } from './core/chat-api.service';

/**
 * Pantalla completa: avisos de estado, preguntas de ejemplo, envío con Enter, respuesta como
 * texto plano y nueva conversación.
 */
describe('App', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  /** Monta la app respondiendo al estado inicial del servidor. */
  async function setup(status = { claudeConfigured: true, bibliotecaAvailable: true }) {
    const fixture = TestBed.createComponent(App);
    const el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(`${API_URL}/chat/status`).flush(status);
    await fixture.whenStable();
    const textarea = () => el.querySelector<HTMLTextAreaElement>('textarea')!;
    const bubbles = () =>
      Array.from(el.querySelectorAll('.bubble:not(.typing)')).map((b) =>
        b.textContent!.replace(/^\s*(Tú:|Asistente:)\s*/, '').trim(),
      );
    /** Escribe en el campo como lo haría una persona (y deja que Angular actualice la vista). */
    const type = async (text: string) => {
      textarea().value = text;
      textarea().dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    return { fixture, el, textarea, bubbles, type };
  }

  it('muestra las preguntas de ejemplo y ningún aviso si todo está configurado', async () => {
    const { el } = await setup();
    const suggestions = Array.from(el.querySelectorAll('.suggestion')).map((s) =>
      s.textContent?.trim(),
    );
    expect(suggestions).toEqual(SUGGESTIONS);
    expect(el.querySelector('.status-banner')).toBeNull();
  });

  it('avisa si falta la clave de Claude', async () => {
    const { el } = await setup({ claudeConfigured: false, bibliotecaAvailable: true });
    expect(el.querySelector('.status-banner')?.textContent).toContain('ANTHROPIC_API_KEY');
  });

  it('avisa si la biblioteca no responde', async () => {
    const { el } = await setup({ claudeConfigured: true, bibliotecaAvailable: false });
    expect(el.querySelector('.status-banner')?.textContent).toContain('biblioteca no responde');
  });

  it('envía una sugerencia y muestra la respuesta como texto, sin interpretar HTML', async () => {
    const { fixture, el, bubbles } = await setup();

    el.querySelector<HTMLButtonElement>('.suggestion')!.click();
    await fixture.whenStable();
    expect(el.querySelector('.typing')).not.toBeNull();

    http.expectOne(`${API_URL}/chat`).flush({ reply: 'Hay 1 vencido:\n- <b>Dune</b>' });
    await fixture.whenStable();

    expect(bubbles()).toEqual([SUGGESTIONS[0], 'Hay 1 vencido:\n- <b>Dune</b>']);
    expect(el.querySelector('.bubble b')).toBeNull();
    expect(el.querySelector('.welcome')).toBeNull();
  });

  it('Enter envía y vacía el campo; Mayús+Enter no envía', async () => {
    const { fixture, textarea, type } = await setup();

    await type('Hola');
    textarea().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true }));
    http.expectNone(`${API_URL}/chat`);

    textarea().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();
    expect(textarea().value).toBe('');
    http.expectOne(`${API_URL}/chat`).flush({ reply: 'Hola' });
  });

  it('nueva conversación vacía el chat y pone el foco en el campo', async () => {
    const { fixture, el, textarea, bubbles, type } = await setup();
    await type('Hola');
    el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${API_URL}/chat`).flush({ reply: 'Hola' });
    await fixture.whenStable();
    expect(bubbles().length).toBe(2);

    el.querySelector<HTMLButtonElement>('[aria-label="Nueva conversación"]')!.click();
    await fixture.whenStable();

    expect(bubbles()).toEqual([]);
    expect(el.querySelector('.welcome')).not.toBeNull();
    expect(document.activeElement).toBe(textarea());
  });
});
