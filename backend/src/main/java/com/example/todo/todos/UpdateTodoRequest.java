package com.example.todo.todos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Body del PATCH: los campos que llegan a null (o no llegan) no se tocan.
 *
 * <p>{@code version} es obligatoria: es la versión que tenía el cliente al editar. Si no coincide con
 * la actual, alguien lo cambió antes y respondemos 409 en lugar de pisar su cambio.
 */
public record UpdateTodoRequest(
        // Como @NotBlank, pero dejando pasar null (= no cambiar el título). Reutiliza su mensaje traducido.
        @Nullable @Size(max = 200) @Pattern(regexp = ".*\\S.*", message = "{jakarta.validation.constraints.NotBlank.message}")
                String title,
        @Nullable Boolean completed,
        @NotNull @Schema(description = "Versión del TODO que el cliente está editando") Long version) {}
