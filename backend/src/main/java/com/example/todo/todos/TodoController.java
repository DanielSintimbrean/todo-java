package com.example.todo.todos;

import com.example.todo.web.ApiProblem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Capa HTTP: traduce requests a llamadas al service y resultados a respuestas.
 * Los errores (404, 409, 400) los convierte en ProblemDetail {@link com.example.todo.web.ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/todos")
@Tag(name = "todos")
@ApiResponse(
        responseCode = "400",
        description = "Request inválida (validación, UUID mal formado, filtro desconocido...)",
        content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    @Operation(summary = "Lista los TODOs, opcionalmente filtrados por estado")
    public List<TodoResponse> list(@RequestParam(required = false) @Nullable TodoStatus status) {
        return todoService.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene un TODO")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    public TodoResponse get(@PathVariable UUID id) {
        return todoService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crea un TODO")
    public TodoResponse create(@Valid @RequestBody CreateTodoRequest request) {
        return todoService.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Modifica título y/o estado. Exige la versión actual (concurrencia optimista)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    @ApiResponse(
            responseCode = "409",
            description = "La versión enviada no es la actual: otra persona lo modificó antes",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    public TodoResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTodoRequest request) {
        // Pattern matching sobre una interfaz sealed: el compilador comprueba que tratamos todos los casos,
        // y los "record patterns" desestructuran el record (como `is Updated { Todo: var todo }` en C#).
        return switch (todoService.update(id, request)) {
            case UpdateResult.Updated(TodoResponse todo) -> todo;
            case UpdateResult.VersionConflict(long currentVersion) ->
                throw new TodoVersionConflictException(id, currentVersion);
        };
    }

    @PostMapping("/{id}/toggle")
    @Operation(summary = "Alterna el estado completado")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    public TodoResponse toggle(@PathVariable UUID id) {
        return todoService.toggle(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Borra un TODO")
    @ApiResponse(responseCode = "204", description = "Borrado")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ApiProblem.class)))
    public void delete(@PathVariable UUID id) {
        todoService.delete(id);
    }
}
