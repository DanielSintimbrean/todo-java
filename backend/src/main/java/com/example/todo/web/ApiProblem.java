package com.example.todo.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Solo para documentación: describe en OpenAPI el JSON que produce un {@link org.springframework.http.ProblemDetail}.
 *
 * <p>El esquema que springdoc deduce de la clase de Spring incluye un campo {@code properties}, pero Jackson
 * lo aplana al serializar ({@code errors} y {@code currentVersion} salen al mismo nivel que {@code title}).
 * Este record refleja lo que de verdad viaja por la red.
 */
@Schema(name = "ProblemDetail", description = "Error en formato RFC 9457 (application/problem+json)")
public record ApiProblem(
        @NotNull String type,
        @NotNull String title,
        @NotNull int status,
        @Nullable String detail,
        @Nullable String instance,
        @Schema(description = "Errores de validación por campo (solo en 400)") @Nullable Map<String, String> errors,
        @Schema(description = "Versión actual del recurso (solo en 409)") @Nullable Long currentVersion) {}
