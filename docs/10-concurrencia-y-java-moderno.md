# 10. Versiones, concurrencia y Java moderno

Imagina que dos pestañas cargan una tarea con el título "Comprar pan". La primera guarda "Comprar pan integral" y la segunda guarda su edición anterior. Queremos que la segunda reciba un conflicto y pueda recargar, en lugar de sobrescribir la primera edición.

## 1. Reproduce el problema del capítulo anterior

Crea una tarea y abre la aplicación en dos pestañas. Edita su título en la primera. Sin recargar la segunda, edítalo también allí. La API anterior acepta ambas porque el PATCH solo indica el nuevo título.

La entidad ya lleva `@Version`, pero eso por sí solo no dice qué versión había visto una pestaña hace un minuto. Protege la escritura entre transacciones, no el tiempo que el usuario estuvo pensando antes de enviar una petición.

## 2. Exige la versión del cliente

Reemplaza `UpdateTodoRequest.java` por esta versión. Todavía omitimos las anotaciones de OpenAPI, que llegan en el capítulo 12:

### `backend/src/main/java/com/example/todo/todos/UpdateTodoRequest.java`

```java
package com.example.todo.todos;

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
        @Nullable
        @Size(max = 200)
        @Pattern(regexp = ".*\\S.*", message = "{jakarta.validation.constraints.NotBlank.message}")
        String title,

        @Nullable Boolean completed,

        @NotNull
        Long version) {}
```

`Long` permite detectar que la versión no se envió, porque en ese caso vale `null` y falla `@NotNull`. Con un `long` primitivo podríamos confundir una ausencia con cero.

## 3. Representa los resultados de la actualización

### `backend/src/main/java/com/example/todo/todos/UpdateResult.java`

```java
package com.example.todo.todos;

/**
 * Resultado de {@link TodoService#update}. Una interfaz {@code sealed} enumera todas sus implementaciones,
 * así que un {@code switch} sobre ella es exhaustivo: si mañana añades un caso, el controller deja de
 * compilar hasta que lo trates (ver {@link TodoController#update}).
 *
 * <p>Un conflicto de versión no es un error inesperado sino un resultado normal de la operación,
 * por eso se devuelve como valor en lugar de lanzarse como excepción.
 */
public sealed interface UpdateResult {

    record Updated(TodoResponse todo) implements UpdateResult {}

    record VersionConflict(long currentVersion) implements UpdateResult {}
}
```

### `backend/src/main/java/com/example/todo/todos/TodoVersionConflictException.java`

```java
package com.example.todo.todos;

import java.util.UUID;

public class TodoVersionConflictException extends RuntimeException {

    private final long currentVersion;

    public TodoVersionConflictException(UUID id, long currentVersion) {
        super("Todo " + id + " was modified by someone else (current version is " + currentVersion + ")");
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
```

La interfaz `sealed` cierra el conjunto de implementaciones posibles. Un resultado es `Updated` o `VersionConflict`. Estos tipos contienen datos distintos y el compilador puede exigir que un switch contemple ambos.

La elección de representar el conflicto como valor está explicada en el comentario de `UpdateResult`: es un resultado esperado de la operación. El controlador lo traducirá al error HTTP.

## 4. Actualiza el servicio

Reemplaza `TodoService.java` por su versión final:

### `backend/src/main/java/com/example/todo/todos/TodoService.java`

```java
package com.example.todo.todos;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio. Cada método público es una transacción: Spring abre la transacción al entrar,
 * hace commit al salir y rollback si sale una RuntimeException. Es lo que en EF Core harías con
 * un DbContext por request más {@code SaveChanges()} al final.
 */
@Service
public class TodoService {

    private final TodoRepository todos;

    // Inyección por constructor. Con un solo constructor no hace falta @Autowired.
    public TodoService(TodoRepository todos) {
        this.todos = todos;
    }

    @Transactional(readOnly = true)
    public List<TodoResponse> list(@Nullable TodoStatus status) {
        List<Todo> result = switch (status) {
            case null -> todos.findAllByOrderByCreatedAtAsc();
            case ACTIVE -> todos.findByCompletedOrderByCreatedAtAsc(false);
            case COMPLETED -> todos.findByCompletedOrderByCreatedAtAsc(true);
        };
        return result.stream().map(TodoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TodoResponse get(UUID id) {
        return TodoResponse.from(findOrThrow(id));
    }

    @Transactional
    public TodoResponse create(CreateTodoRequest request) {
        Todo todo = todos.save(new Todo(request.title()));
        return TodoResponse.from(todo);
    }

    @Transactional
    public UpdateResult update(UUID id, UpdateTodoRequest request) {
        Todo todo = findOrThrow(id);

        long currentVersion = requireNonNull(todo.getVersion());
        if (currentVersion != request.version()) {
            return new UpdateResult.VersionConflict(currentVersion);
        }

        String title = request.title();
        if (title != null) {
            todo.rename(title);
        }
        Boolean completed = request.completed();
        if (completed != null) {
            todo.setCompleted(completed);
        }

        // No hay save(): la entidad está "managed" y Hibernate detecta los cambios (dirty checking)
        // y lanza el UPDATE al hacer commit. Forzamos el flush aquí solo para que la respuesta
        // lleve la versión y el updatedAt nuevos, que se calculan al escribir en la base de datos.
        todos.flush();
        return new UpdateResult.Updated(TodoResponse.from(todo));
    }

    @Transactional
    public TodoResponse toggle(UUID id) {
        Todo todo = findOrThrow(id);
        todo.toggle();
        todos.flush();
        return TodoResponse.from(todo);
    }

    @Transactional
    public void delete(UUID id) {
        todos.delete(findOrThrow(id));
    }

    private Todo findOrThrow(UUID id) {
        return todos.findById(id).orElseThrow(() -> new TodoNotFoundException(id));
    }
}
```

La comparación de versiones ocurre antes de modificar la entidad. `request.version()` se desempaqueta a un número al compararlo con `long`; la validación del controlador ha rechazado previamente el `null`.

Hay dos comprobaciones distintas. La comparación explícita detecta que el cliente ya llega atrasado. `@Version` hace que Hibernate compruebe la versión al ejecutar el UPDATE, lo que también cubre dos transacciones que leyeron la misma versión y compiten por escribir.

Un cambio real incrementa la versión. No presupongas que un PATCH que deja exactamente los mismos valores provocará un UPDATE o aumentará la versión.

## 5. Resuelve ambos resultados en el controlador

En `TodoController`, reemplaza el método `update` junto con su anotación por este bloque:

```java
@PatchMapping("/{id}")
public TodoResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTodoRequest request) {
    return switch (todoService.update(id, request)) {
        case UpdateResult.Updated(TodoResponse todo) -> todo;
        case UpdateResult.VersionConflict(long currentVersion) ->
            throw new TodoVersionConflictException(id, currentVersion);
    };
}
```

Las ramas desestructuran los records para extraer sus datos. No hay `default`: si añades otro resultado permitido, el compilador te obligará a tratarlo.

## 6. Completa el manejo de errores

Sustituye `ApiExceptionHandler.java` por su versión final:

### `backend/src/main/java/com/example/todo/web/ApiExceptionHandler.java`

```java
package com.example.todo.web;

import com.example.todo.todos.TodoNotFoundException;
import com.example.todo.todos.TodoVersionConflictException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte excepciones en respuestas {@code application/problem+json} (RFC 9457), como un
 * middleware de excepciones en ASP.NET Core.
 *
 * <p>Heredar de {@link ResponseEntityExceptionHandler} hace que los errores propios de Spring MVC
 * (JSON mal formado, UUID inválido, método no permitido...) también salgan como ProblemDetail.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail handleNotFound(TodoNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Todo not found");
        return problem;
    }

    /** El cliente mandó una versión antigua (comprobado a mano en el service). */
    @ExceptionHandler
    ProblemDetail handleVersionConflict(TodoVersionConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Version conflict");
        problem.setProperty("currentVersion", ex.getCurrentVersion());
        return problem;
    }

    /** Dos transacciones modificaron la misma fila a la vez y @Version lo detectó al hacer el UPDATE. */
    @ExceptionHandler
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The todo was modified concurrently, reload and try again");
        problem.setTitle("Version conflict");
        return problem;
    }

    /** Añade al ProblemDetail estándar un mapa {@code errors} campo → mensaje para mostrarlo en la UI. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            errors.putIfAbsent(error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"));
        }
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
```

Un conflicto de versión del cliente devuelve 409 con `currentVersion`. Un conflicto detectado al escribir por Hibernate devuelve 409 sin prometer esa propiedad. Son caminos distintos.

Los errores de red o de un proxy no pasan por este advice. Por eso el frontend debe tolerar también respuestas sin este formato.

## 7. Envía la versión desde React

En `frontend/src/api/todos.ts`, cambia la firma y el cuerpo de `updateTodo`:

```ts
export function updateTodo(id: string, title: string, version: number): Promise<Todo> {
  return requestJson<Todo>(`/api/todos/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title, version }),
  });
}
```

En la función `edit` de `App.tsx`, cambia la llamada a `updateTodo(todo.id, nextTitle, todo.version)`. Es la versión cargada por esa pantalla, no una versión consultada justo antes del PATCH.

`toggle` no recibe la versión del cliente en este proyecto. Hibernate sigue protegiendo su escritura concurrente, pero alternar no ofrece la misma comprobación explícita de una pestaña antigua que PATCH.

## 8. Declara la nulabilidad por paquete

Crea los tres `package-info.java` siguientes. La dependencia JSpecify se añadió en el capítulo 8.

### `backend/src/main/java/com/example/todo/package-info.java`

```java
@NullMarked
package com.example.todo;

import org.jspecify.annotations.NullMarked;
```

### `backend/src/main/java/com/example/todo/todos/package-info.java`

```java
/**
 * Todo lo relativo a los TODOs: entidad, repositorio, service, controller y DTOs.
 *
 * <p>{@code @NullMarked} hace que todos los tipos de este paquete sean no-null por defecto
 * (como {@code <Nullable>enable</Nullable>} en C#). Lo que puede ser null se marca con {@code @Nullable}.
 */
@NullMarked
package com.example.todo.todos;

import org.jspecify.annotations.NullMarked;
```

### `backend/src/main/java/com/example/todo/web/package-info.java`

```java
/** Piezas transversales de la capa HTTP: manejo de errores y configuración de OpenAPI. */
@NullMarked
package com.example.todo.web;

import org.jspecify.annotations.NullMarked;
```

`@NullMarked` declara que los tipos de referencia del paquete se consideran no nulos salvo que lleven `@Nullable`. No aplica automáticamente a subpaquetes, por eso hay tres ficheros. Estas anotaciones ayudan al IDE y a analizadores; `javac` por sí solo no convierte todas las posibles nulidades en errores.

No confundas `@Nullable`, que describe el código, con `@NotNull`, que participa en validación cuando invocas un validador, como hace `@Valid` con una petición.

## Comprobación

Crea otra tarea. Copia su UUID y la versión devuelta. En Bash, ajusta ambos valores:

```sh
TODO_ID='pega-aqui-el-uuid'
TODO_VERSION=0
curl -i -X PATCH "http://localhost:8080/api/todos/$TODO_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Primera edición\",\"version\":$TODO_VERSION}"
curl -i -X PATCH "http://localhost:8080/api/todos/$TODO_ID" \
  -H 'Content-Type: application/json' \
  -d "{\"title\":\"Edición antigua\",\"version\":$TODO_VERSION}"
```

La primera edición devuelve 200 con una versión nueva. La segunda devuelve 409 y no cambia el título. Un PATCH sin versión devuelve 400. Las dos pestañas deben reproducir ahora ese conflicto.

[Anterior](09-crud-y-filtros.md) · [Índice](README.md) · [Siguiente: tests del backend](11-tests-backend.md)
