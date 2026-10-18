package com.example.todo.todos;

import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Lo que devuelve la API. Un record es un tipo inmutable con constructor, getters
 * ({@code id()}, {@code title()}...), equals/hashCode y toString generados (como un {@code record} de C#).
 *
 * <p>Los {@code @NotNull} no validan nada aquí: sirven para que springdoc marque los campos como
 * {@code required} en openapi.json y openapi-typescript genere tipos sin {@code ?}.
 */
public record TodoResponse(
        @NotNull UUID id,
        @NotNull String title,
        @NotNull boolean completed,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        @NotNull long version) {

    /** Solo vale para entidades ya persistidas (con id, fechas y versión asignados). */
    static TodoResponse from(Todo todo) {
        return new TodoResponse(
                requireNonNull(todo.getId()),
                todo.getTitle(),
                todo.isCompleted(),
                requireNonNull(todo.getCreatedAt()),
                requireNonNull(todo.getUpdatedAt()),
                requireNonNull(todo.getVersion()));
    }
}
