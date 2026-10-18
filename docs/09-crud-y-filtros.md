# 09. Consultar, editar, completar, borrar y filtrar

La creación y la lista ya usan PostgreSQL. Añadiremos el resto de operaciones manteniendo el cliente con `fetch`. En este capítulo PATCH todavía no comprueba la versión que vio el usuario; incorporaremos ese requisito después.

## 1. Define una actualización parcial

Crea `backend/src/main/java/com/example/todo/todos/UpdateTodoRequest.java`:

```java
package com.example.todo.todos;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdateTodoRequest(
        @Nullable @Size(max = 200)
        @Pattern(regexp = ".*\\S.*", message = "{jakarta.validation.constraints.NotBlank.message}")
        String title,
        @Nullable Boolean completed) {}
```

En este contrato, omitir un campo o enviarlo como `null` significa conservarlo. `Boolean` permite distinguir `null` de `false`; el primitivo `boolean` no tiene un tercer estado. La expresión regular exige un carácter que no sea espacio cuando hay título. `@NotBlank` no serviría aquí porque también rechazaría `null`.

## 2. Distingue una tarea inexistente

### `backend/src/main/java/com/example/todo/todos/TodoNotFoundException.java`

```java
package com.example.todo.todos;

import java.util.UUID;

public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(UUID id) {
        super("Todo " + id + " not found");
    }
}
```

En `ApiExceptionHandler` añade los imports de `TodoNotFoundException`, `HttpStatus` y `ExceptionHandler`, y este método dentro de la clase:

```java
@ExceptionHandler
ProblemDetail handleNotFound(TodoNotFoundException ex) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Todo not found");
    return problem;
}
```

La excepción representa que la búsqueda no encontró una tarea. El advice decide que eso se expone como un 404 HTTP.

## 3. Completa el servicio

Reemplaza `TodoService.java` por esta versión intermedia:

```java
package com.example.todo.todos;

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
    public TodoResponse update(UUID id, UpdateTodoRequest request) {
        Todo todo = findOrThrow(id);

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
        return TodoResponse.from(todo);
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

`findById` devuelve un `Optional<Todo>`. `orElseThrow` extrae la tarea si existe y lanza la excepción si está vacío. El método privado concentra esa decisión para obtener, editar, alternar y borrar.

El `switch` de `list` devuelve un valor para cada estado y trata explícitamente `null`, que representa la ausencia de filtro.

En `update` y `toggle` no llamamos a `save`. La entidad se ha cargado dentro de una transacción y está gestionada por Hibernate. Hibernate detecta sus cambios y escribe un UPDATE. A esto se le llama dirty checking.

`flush()` adelanta la escritura para que el DTO incluya la nueva versión y fecha. No confirma la transacción. El commit ocurre al salir correctamente del método transaccional.

## 4. Expón las operaciones HTTP

Reemplaza `TodoController.java`:

```java
package com.example.todo.todos;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todos")
public class TodoController {
    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public List<TodoResponse> list(@RequestParam(required = false) @Nullable TodoStatus status) {
        return todoService.list(status);
    }

    @GetMapping("/{id}")
    public TodoResponse get(@PathVariable UUID id) {
        return todoService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse create(@Valid @RequestBody CreateTodoRequest request) {
        return todoService.create(request);
    }

    @PatchMapping("/{id}")
    public TodoResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTodoRequest request) {
        return todoService.update(id, request);
    }

    @PostMapping("/{id}/toggle")
    public TodoResponse toggle(@PathVariable UUID id) {
        return todoService.toggle(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        todoService.delete(id);
    }
}
```

Aquí se usa un import con `*` para abreviar el ejemplo. En el código final encontrarás cada anotación importada por separado.

`@PathVariable` convierte el fragmento `{id}` en UUID. `@RequestParam` lee `?status=ACTIVE`. Spring convierte el texto al enum y rechaza un UUID o un estado desconocido con 400. DELETE devuelve 204 y no lleva cuerpo.

## 5. Prueba la API antes de ampliar React

Crea una tarea con el POST de los capítulos anteriores. Copia su identificador en esta variable de Bash:

```sh
TODO_ID='pega-aqui-el-uuid-devuelto'
curl -i "http://localhost:8080/api/todos/$TODO_ID"
curl -i -X PATCH "http://localhost:8080/api/todos/$TODO_ID" \
  -H 'Content-Type: application/json' -d '{"title":"Título editado"}'
curl -i -X POST "http://localhost:8080/api/todos/$TODO_ID/toggle"
curl -i 'http://localhost:8080/api/todos?status=COMPLETED'
curl -i -X DELETE "http://localhost:8080/api/todos/$TODO_ID"
curl -i "http://localhost:8080/api/todos/$TODO_ID"
```

El último GET debe devolver 404. Repite el PATCH con `{"completed":false}` y con `{}` antes de borrar otra tarea. Observa qué cambia y qué permanece.

## 6. Añade las funciones fetch

En `frontend/src/api/todos.ts`, sustituye `listTodos` y añade las otras funciones. Conserva `Todo`, `requestJson` y `createTodo`.

```ts
export type TodoStatus = "ACTIVE" | "COMPLETED";

export function listTodos(status?: TodoStatus): Promise<Todo[]> {
  const query = status ? `?status=${status}` : "";
  return requestJson<Todo[]>(`/api/todos${query}`);
}

export function updateTodo(id: string, title: string): Promise<Todo> {
  return requestJson<Todo>(`/api/todos/${id}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title }),
  });
}

export function toggleTodo(id: string): Promise<Todo> {
  return requestJson<Todo>(`/api/todos/${id}/toggle`, { method: "POST" });
}

export async function deleteTodo(id: string): Promise<void> {
  const response = await fetch(`/api/todos/${id}`, { method: "DELETE" });
  if (!response.ok) throw new Error(`Error HTTP ${response.status}`);
}
```

DELETE no usa `requestJson` porque un 204 no tiene JSON que leer.

## 7. Conecta acciones y filtros

Reemplaza `frontend/src/App.tsx` por esta versión sencilla. Usaremos `prompt` para concentrarnos ahora en HTTP; el editor integrado llega con la interfaz final.

```tsx
import { type FormEvent, useEffect, useState } from "react";
import {
  createTodo, deleteTodo, listTodos, toggleTodo, updateTodo,
  type Todo, type TodoStatus,
} from "./api/todos";

export default function App() {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [title, setTitle] = useState("");
  const [status, setStatus] = useState<TodoStatus | undefined>();
  const [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listTodos(status)
      .then((data) => { if (active) setTodos(data); })
      .catch((error: unknown) => {
        if (active) setError(error instanceof Error ? error.message : "Error inesperado");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [status, revision]);

  async function mutate(operation: () => Promise<unknown>) {
    setBusy(true);
    setError(null);
    try {
      await operation();
    } catch (error) {
      setError(error instanceof Error ? error.message : "Error inesperado");
    } finally {
      setRevision((value) => value + 1);
      setBusy(false);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void mutate(async () => {
      await createTodo(title);
      setTitle("");
    });
  }

  function edit(todo: Todo) {
    const nextTitle = window.prompt("Nuevo título", todo.title);
    if (nextTitle !== null) void mutate(() => updateTodo(todo.id, nextTitle));
  }

  return (
    <main>
      <h1>TODOs</h1>
      <form onSubmit={submit}>
        <input aria-label="Nuevo TODO" value={title}
          onChange={(event) => setTitle(event.target.value)} />
        <button disabled={busy || loading}>Añadir</button>
      </form>
      <fieldset disabled={busy || loading}>
        <legend>Filtrar tareas</legend>
        <button onClick={() => setStatus(undefined)}>Todos</button>
        <button onClick={() => setStatus("ACTIVE")}>Activos</button>
        <button onClick={() => setStatus("COMPLETED")}>Completados</button>
      </fieldset>
      {error && <p role="alert">{error}</p>}
      {loading && <p>Cargando...</p>}
      {!loading && todos.length === 0 && <p>No hay tareas.</p>}
      <ul>
        {todos.map((todo) => (
          <li key={todo.id}>
            <input type="checkbox" aria-label={`Completar ${todo.title}`}
              checked={todo.completed} disabled={busy || loading}
              onChange={() => void mutate(() => toggleTodo(todo.id))} />
            <span>{todo.title}</span>
            <button disabled={busy || loading} onClick={() => edit(todo)}>Editar</button>
            <button disabled={busy || loading}
              onClick={() => void mutate(() => deleteTodo(todo.id))}>Borrar</button>
          </li>
        ))}
      </ul>
    </main>
  );
}
```

Cada mutación incrementa `revision`, que vuelve a ejecutar la carga con el filtro actual. También recargamos después de un error para recuperar el estado del servidor. Conservamos el error de la mutación durante esa recarga para que puedas leerlo. Esta solución es deliberadamente pequeña; todavía no pretende ser una caché general.

## Comprobación

Desde la página puedes crear, editar, marcar, desmarcar, borrar y filtrar. Después de completar una tarea, debe desaparecer del filtro de activos. Todavía puedes sobrescribir una edición hecha desde otra pestaña, porque PATCH no envía la versión que vio el usuario. Ese será el siguiente problema que resolveremos.

[Anterior](08-persistencia.md) · [Índice](README.md) · [Siguiente: concurrencia y Java moderno](10-concurrencia-y-java-moderno.md)
