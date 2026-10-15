package com.biblioteca.chatbot.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Conexión con la API del Sistema de Biblioteca y sus reglas de préstamo.
 *
 * <p>Las reglas no se pueden consultar por la API, así que se configuran aquí con los mismos
 * valores que {@code library.loans.*} del proyecto biblioteca.
 *
 * @param baseUrl           URL base de la API, p. ej. {@code http://localhost:8080/api}
 * @param timeout           espera máxima de cada consulta a la biblioteca
 * @param loanDays          días de plazo de un préstamo
 * @param maxActiveLoans    préstamos activos simultáneos por usuario
 * @param renewalWindowDays un préstamo solo se renueva cuando faltan estos días o menos para vencer
 */
@Validated
@ConfigurationProperties(prefix = "biblioteca")
public record BibliotecaProperties(@DefaultValue("http://localhost:8080/api") @NotBlank String baseUrl,
                                   @DefaultValue("10s") Duration timeout,
                                   @DefaultValue("14") @Min(1) int loanDays,
                                   @DefaultValue("3") @Min(1) int maxActiveLoans,
                                   @DefaultValue("5") @Min(0) int renewalWindowDays) {
}
