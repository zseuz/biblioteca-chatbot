import { provideHttpClient } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';

/**
 * Configuración global (main.ts la usa al arrancar): errores no controlados del navegador y
 * cliente HTTP para hablar con el backend del asistente.
 */
export const appConfig: ApplicationConfig = {
  providers: [provideBrowserGlobalErrorListeners(), provideHttpClient()],
};
