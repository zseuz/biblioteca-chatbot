import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { App } from './app';
import { SUGGESTIONS } from './chat/chat.component';
import { API_URL } from './core/chat-api.service';

/**
 * Pantalla completa: estado de la conexión, preguntas de ejemplo, envío con Enter, respuesta con
 * listas pero sin interpretar HTML, y nueva conversación.
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
  async function setup(status = { bibliotecaAvailable: true }) {
    const fixture = TestBed.createComponent(App);
    const el = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(`${API_URL}/chat/status`).flush(status);
    await fixture.whenStable();
    const textarea = () => el.querySelector<HTMLTextAreaElement>('textarea')!;
    /** Texto de cada mensaje (sin el indicador de escritura), con los espacios normalizados. */
    const bubbles = () =>
      Array.from(el.querySelectorAll('.bubble:not(.typing)')).map((b) =>
        b.textContent!.replace(/\s+/g, ' ').trim(),
      );
    /** Escribe en el campo como lo haría una persona (y deja que Angular actualice la vista). */
    const type = async (text: string) => {
      textarea().value = text;
      textarea().dispatchEvent(new Event('input'));
      await fixture.whenStable();
    };
    return { fixture, el, textarea, bubbles, type };
  }

  it('muestra las preguntas de ejemplo y el catálogo en línea, sin avisos', async () => {
    const { el } = await setup();
    const suggestions = Array.from(el.querySelectorAll('.suggestion .question')).map((s) =>
      s.textContent?.trim(),
    );
    expect(suggestions).toEqual(SUGGESTIONS);
    expect(el.querySelector('.status-banner')).toBeNull();
    expect(el.querySelector('.status')?.getAttribute('data-state')).toBe('online');
    expect(el.querySelector('.status')?.textContent).toContain('Catálogo en línea');
  });

  it('avisa si la biblioteca no responde', async () => {
    const { el } = await setup({ bibliotecaAvailable: false });
    expect(el.querySelector('.status-banner')?.textContent).toContain('biblioteca no responde');
    expect(el.querySelector('.status')?.getAttribute('data-state')).toBe('degraded');
  });

  it('envía una sugerencia y muestra la lista de la respuesta sin interpretar HTML', async () => {
    const { fixture, el, bubbles } = await setup();

    el.querySelector<HTMLButtonElement>('.suggestion')!.click();
    await fixture.whenStable();
    expect(el.querySelector('.typing')).not.toBeNull();

    http
      .expectOne(`${API_URL}/chat`)
      .flush({ reply: 'Hay 2 vencidos:\n- <b>Dune</b>\n- «Rayuela»' });
    await fixture.whenStable();

    expect(bubbles()[0]).toBe(SUGGESTIONS[0]);
    const reply = el.querySelectorAll('.message')[1];
    expect(reply.querySelector('.who')?.textContent?.trim()).toBe('Asistente');
    expect(reply.querySelector('.bubble p')?.textContent).toBe('Hay 2 vencidos:');
    const items = Array.from(reply.querySelectorAll('.bubble li')).map((li) => li.textContent);
    expect(items).toEqual(['<b>Dune</b>', '«Rayuela»']);
    expect(el.querySelector('.bubble b')).toBeNull();
    expect(el.querySelector('.welcome')).toBeNull();
    expect(el.querySelector('.typing')).toBeNull();
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

  it('vacía el campo aunque se pulse Enter antes de que Angular repinte', async () => {
    const { fixture, textarea } = await setup();

    // Escribir (o pegar) y enviar en el mismo instante, sin esperar a la detección de cambios.
    textarea().value = '¿Hay vencidos?';
    textarea().dispatchEvent(new Event('input'));
    textarea().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();

    expect(textarea().value).toBe('');
    const req = http.expectOne(`${API_URL}/chat`);
    expect(req.request.body.messages).toEqual([{ role: 'user', content: '¿Hay vencidos?' }]);
    req.flush({ reply: 'No hay préstamos vencidos.' });
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
