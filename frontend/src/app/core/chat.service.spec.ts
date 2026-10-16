import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_URL } from './chat-api.service';
import { ChatService } from './chat.service';

/**
 * Estado de la conversación: qué se envía al backend, cómo se añaden las respuestas y cómo se
 * gestionan los errores. HttpTestingController captura las peticiones y la prueba responde.
 */
describe('ChatService', () => {
  let chat: ChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    chat = TestBed.inject(ChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('envía la conversación completa y añade la respuesta', () => {
    expect(chat.send('  Hola  ')).toBe(true);
    expect(chat.loading()).toBe(true);
    http.expectOne(`${API_URL}/chat`).flush({ reply: '¿En qué te ayudo?' });

    chat.send('¿Hay libros de Dune?');
    const req = http.expectOne(`${API_URL}/chat`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body.messages).toEqual([
      { role: 'user', content: 'Hola' },
      { role: 'assistant', content: '¿En qué te ayudo?' },
      { role: 'user', content: '¿Hay libros de Dune?' },
    ]);
    req.flush({ reply: 'Sí, 2 ejemplares.' });

    expect(chat.loading()).toBe(false);
    expect(chat.messages().at(-1)).toEqual({ role: 'assistant', content: 'Sí, 2 ejemplares.' });
  });

  it('ignora mensajes vacíos y no envía otro mientras espera respuesta', () => {
    expect(chat.send('   ')).toBe(false);
    chat.send('Primera');
    expect(chat.send('Segunda')).toBe(false);
    http.expectOne(`${API_URL}/chat`).flush({ reply: 'Ok' });
    expect(chat.messages().map((m) => m.content)).toEqual(['Primera', 'Ok']);
  });

  it('guarda el mensaje de error del backend, actualiza el estado y permite reintentar', () => {
    chat.send('¿Préstamos vencidos?');
    http
      .expectOne(`${API_URL}/chat`)
      .flush(
        { message: 'No se pudo conectar con la API de la biblioteca' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    http.expectOne(`${API_URL}/chat/status`).flush({ bibliotecaAvailable: false });

    expect(chat.error()).toBe('No se pudo conectar con la API de la biblioteca');
    expect(chat.status()).toEqual({ bibliotecaAvailable: false });

    chat.retry();
    const retry = http.expectOne(`${API_URL}/chat`);
    expect(retry.request.body.messages).toEqual([
      { role: 'user', content: '¿Préstamos vencidos?' },
    ]);
    retry.flush({ reply: 'Ninguno.' });
    expect(chat.error()).toBeNull();
  });

  it('marca el servidor como desconectado si no responde al pedir el estado', () => {
    chat.refreshStatus();
    http.expectOne(`${API_URL}/chat/status`).error(new ProgressEvent('error'));
    expect(chat.offline()).toBe(true);
    expect(chat.status()).toBeNull();
  });

  it('reset vacía la conversación y el error', () => {
    chat.send('Hola');
    http.expectOne(`${API_URL}/chat`).flush({ reply: 'Hola' });
    chat.reset();
    expect(chat.hasMessages()).toBe(false);
    expect(chat.error()).toBeNull();
  });
});
