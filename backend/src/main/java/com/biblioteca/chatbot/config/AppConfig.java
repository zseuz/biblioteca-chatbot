package com.biblioteca.chatbot.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Componentes compartidos: clientes HTTP (Claude y biblioteca), reloj y política CORS. */
@Configuration
public class AppConfig implements WebMvcConfigurer {

    /** Orígenes autorizados a llamar a esta API desde el navegador (el frontend Angular). */
    @Value("${app.cors.allowed-origins}")
    private String[] allowedOrigins;

    /** Reloj inyectable: en las pruebas se usa uno fijo para controlar la fecha de hoy. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Cliente de la API de Claude. Solo se crea si hay clave; sin ella el asistente responde 503
     * explicando qué falta. Spring lo cierra al apagar la aplicación.
     */
    @Bean
    @ConditionalOnExpression("!'${chat.api-key:}'.isBlank()")
    AnthropicClient anthropicClient(ChatProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .build();
    }

    /** Cliente HTTP de la API de la biblioteca, con la URL base y los tiempos de espera configurados. */
    @Bean
    RestClient bibliotecaRestClient(BibliotecaProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.timeout());
        requestFactory.setReadTimeout(properties.timeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /** CORS: el frontend (puerto 4201) solo puede llamar a {@code /api/**}. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST")
                .maxAge(3600);
    }
}
