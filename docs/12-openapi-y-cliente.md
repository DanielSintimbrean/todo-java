# 12. OpenAPI y cliente TypeScript generado

Hasta ahora escribiste a mano el tipo `Todo` y cada URL en TypeScript. Vamos a generar esos datos desde una descripción de la API. El cliente generado seguirá haciendo peticiones con fetch.

## 1. Publica la descripción del backend

Añade en `<properties>` del POM:

```xml
<springdoc.version>3.1.1</springdoc.version>
```

Añade en `<dependencies>`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

Usamos la versión fijada por este repositorio. En la configuración de `application.properties` añade:

```properties
springdoc.writer-with-default-pretty-printer=true
springdoc.writer-with-order-by-keys=true
springdoc.default-produces-media-type=application/json
```

Crea estos dos ficheros:

### `backend/src/main/java/com/example/todo/web/OpenApiConfig.java`

```java
package com.example.todo.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Todo API").version("v1"))
                // Servidor fijo: si no, springdoc pone http://localhost:<puerto> y openapi.json cambiaría en cada test.
                .servers(List.of(new Server().url("/")));
    }
}
```

### `backend/src/main/java/com/example/todo/web/ApiProblem.java`

```java
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

        @Schema(description = "Errores de validación por campo (solo en 400)") @Nullable
        Map<String, String> errors,

        @Schema(description = "Versión actual del recurso (solo en 409)") @Nullable
        Long currentVersion) {}
```

`OpenApiConfig` fija el servidor del documento como `/` para no incluir un puerto aleatorio del test. La configuración de orden y formato reduce diferencias de generación.

`ApiProblem` solo documenta la forma del error. No sustituye a `ProblemDetail` ni se construye en el advice. Existe porque las propiedades adicionales de ProblemDetail se aplanan en el JSON, mientras que el esquema deducido automáticamente puede describir un objeto `properties` que no viaja por la red.

## 2. Da nombre estable a las operaciones

Reemplaza `UpdateTodoRequest` por su versión final con la descripción de versión y `TodoController` por la versión con anotaciones OpenAPI:

### `backend/src/main/java/com/example/todo/todos/UpdateTodoRequest.java`

```java
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
        @Nullable
        @Size(max = 200)
        @Pattern(regexp = ".*\\S.*", message = "{jakarta.validation.constraints.NotBlank.message}")
        String title,

        @Nullable Boolean completed,

        @NotNull @Schema(description = "Versión del TODO que el cliente está editando")
        Long version) {}
```

### `backend/src/main/java/com/example/todo/todos/TodoController.java`

```java
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
 *
 * <p>Cada {@code operationId} da nombre a las funciones del cliente generado en el frontend
 * ({@code listTodosOptions}, {@code createTodoMutation}...).
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
    @Operation(operationId = "listTodos", summary = "Lista los TODOs, opcionalmente filtrados por estado")
    @ApiResponse(responseCode = "200", description = "OK")
    public List<TodoResponse> list(@RequestParam(required = false) @Nullable TodoStatus status) {
        return todoService.list(status);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getTodo", summary = "Obtiene un TODO")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    public TodoResponse get(@PathVariable UUID id) {
        return todoService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTodo", summary = "Crea un TODO")
    public TodoResponse create(@Valid @RequestBody CreateTodoRequest request) {
        return todoService.create(request);
    }

    @PatchMapping("/{id}")
    @Operation(
            operationId = "updateTodo",
            summary = "Modifica título y/o estado. Exige la versión actual (concurrencia optimista)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    @ApiResponse(
            responseCode = "409",
            description = "La versión enviada no es la actual: otra persona lo modificó antes",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
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
    @Operation(operationId = "toggleTodo", summary = "Alterna el estado completado")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    public TodoResponse toggle(@PathVariable UUID id) {
        return todoService.toggle(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteTodo", summary = "Borra un TODO")
    @ApiResponse(responseCode = "204", description = "Borrado")
    @ApiResponse(
            responseCode = "404",
            description = "No existe",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ApiProblem.class)))
    public void delete(@PathVariable UUID id) {
        todoService.delete(id);
    }
}
```

`@Operation(operationId = "listTodos")` fija el nombre que usará el generador. Los `@ApiResponse` describen estados y esquemas de error. Estas anotaciones documentan el contrato; el comportamiento HTTP sigue estando en las anotaciones MVC y en el servicio.

Reinicia Java y abre `http://localhost:8080/swagger-ui.html`. Prueba una creación y un PATCH. Abre también `http://localhost:8080/v3/api-docs` para ver la descripción JSON que consumirá el generador.

Retira ahora `HelloController.java`: ya ha cumplido su objetivo y la API final no lo incluye.

## 3. Genera el contrato mediante un test

### `backend/src/test/java/com/example/todo/OpenApiDocumentTest.java`

```java
package com.example.todo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Genera {@code backend/openapi.json}, el contrato del que sale el cliente TypeScript del frontend.
 * Se versiona en git: {@code mise run check} falla si el fichero cambia y no se ha commiteado.
 */
@IntegrationTest
class OpenApiDocumentTest {

    @Autowired
    RestTestClient client;

    @Test
    void writesOpenApiDocument() throws IOException {
        String json = client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Maven ejecuta los tests con backend/ como directorio de trabajo.
        Files.writeString(Path.of("openapi.json"), json.strip() + "\n");
    }
}
```

Este test usa el servidor y PostgreSQL temporales del capítulo anterior. Pide la documentación y escribe `backend/openapi.json`. No necesita que tengas abierto el backend local.

Desde la raíz:

```sh
cd backend
mise exec -- ./mvnw -Dtest=OpenApiDocumentTest test
cd ..
```

Comprueba que el JSON contiene `listTodos`, `createTodo`, `getTodo`, `updateTodo`, `toggleTodo` y `deleteTodo` como identificadores de operación.

## 4. Instala y configura Hey API

Desde la raíz:

```sh
cd frontend
mise exec -- vp add @tanstack/react-query@5.104.3
mise exec -- vp add -D @hey-api/openapi-ts@0.0.0-next-20260930190945
```

TanStack Query se instala aquí porque el generador producirá helpers que lo importan; aprenderemos a usarlos en el próximo capítulo. Conservamos la prerelease del generador fijada en el proyecto. No estamos recomendando que sea la versión más reciente.

Añade a `scripts` de `frontend/package.json`, conservando los demás scripts:

```json
"api:generate": "openapi-ts"
```

Crea la configuración y ejecuta el script desde `frontend/`:

### `frontend/openapi-ts.config.ts`

```ts
import { defineConfig } from "@hey-api/openapi-ts";

// Genera src/api/generated/ a partir del contrato del backend: tipos, cliente fetch y
// helpers de TanStack Query (queryOptions, queryKey y mutationOptions por operación).
export default defineConfig({
  input: "../backend/openapi.json",
  output: "src/api/generated",
  plugins: [
    // Sin baseUrl: las rutas ya empiezan por /api, que el dev server de Vite redirige al backend.
    // (Con el `servers: [{ url: "/" }]` del contrato saldría "//api/todos", una URL de otro host.)
    { name: "@hey-api/client-fetch", baseUrl: false },
    "@tanstack/react-query",
  ],
});
```

```sh
mise exec -- vp run api:generate
```

Se crea `src/api/generated/`. No edites sus ficheros a mano. `baseUrl: false` conserva las URLs relativas `/api/...`; con una base `/` mal concatenada podrías obtener `//api/todos`, que el navegador interpreta como otro host.

## 5. Normaliza los errores del cliente

Crea estos dos ficheros:

### `frontend/src/api/client.ts`

```ts
import type { ListTodosData, ProblemDetail, TodoResponse } from "./generated";
import { client } from "./generated/client.gen";
import { toProblem } from "./problem";

export type Todo = TodoResponse;
export type Problem = ProblemDetail;
export type TodoStatus = NonNullable<ListTodosData["query"]>["status"];

/**
 * El backend siempre responde los errores como ProblemDetail, pero lo que hay por medio no
 * (p. ej. un 502 text/plain vacío del proxy de Vite si el backend está parado, o un fallo de red).
 * Este interceptor convierte cualquier error en un ProblemDetail para que toda la app trate un único tipo.
 * Se registra al importar este módulo (main.tsx lo importa al arrancar).
 */
client.interceptors.error.use((error, response) => toProblem(error, response));
```

### `frontend/src/api/problem.ts`

```ts
import type { Problem } from "./client";

/**
 * Normaliza un error en un ProblemDetail. Si ya lo es, se devuelve tal cual; si hay respuesta HTTP
 * se construye a partir de su estado; si no, es que la petición ni siquiera llegó a hacerse.
 */
export function toProblem(error: unknown, response?: Response): Problem {
  if (isProblem(error)) {
    return error;
  }
  if (response) {
    return {
      type: "about:blank",
      title: response.statusText || "Error",
      status: response.status,
      detail: typeof error === "string" && error !== "" ? error : undefined,
    };
  }
  return {
    type: "about:blank",
    title: error instanceof Error ? "Network error" : "Unexpected error",
    status: 0,
    detail: error instanceof Error ? error.message : undefined,
  };
}

function isProblem(error: unknown): error is Problem {
  return typeof error === "object" && error !== null && "title" in error && "status" in error;
}
```

Importa `./api/client` una vez desde `main.tsx` para registrar el interceptor. Los imports de tipos se eliminan al construir JavaScript, por eso la referencia de tipos entre estos módulos no introduce por sí sola una inicialización circular.

`toProblem` conserva un problema recibido, construye uno a partir del estado HTTP si el proxy devuelve otro formato, o representa un fallo de red con estado interno cero. Cero no es un código HTTP recibido del servidor.

## 6. Sustituye las funciones escritas a mano

Para mantener funcionando la página del capítulo 9 antes de pasar a Query, reemplaza temporalmente `frontend/src/api/todos.ts` por:

```ts
import type { Todo, TodoStatus } from "./client";
import * as sdk from "./generated";
export type { Todo, TodoStatus } from "./client";

export async function listTodos(status?: TodoStatus): Promise<Todo[]> {
  const { data } = await sdk.listTodos({ query: { status }, throwOnError: true });
  return data;
}

export async function createTodo(title: string): Promise<Todo> {
  const { data } = await sdk.createTodo({ body: { title }, throwOnError: true });
  return data;
}

export async function updateTodo(id: string, title: string, version: number): Promise<Todo> {
  const { data } = await sdk.updateTodo({ path: { id }, body: { title, version }, throwOnError: true });
  return data;
}

export async function toggleTodo(id: string): Promise<Todo> {
  const { data } = await sdk.toggleTodo({ path: { id }, throwOnError: true });
  return data;
}

export async function deleteTodo(id: string): Promise<void> {
  await sdk.deleteTodo({ path: { id }, throwOnError: true });
}
```

El SDK recibe objetos `path`, `query` y `body` tipados. `throwOnError: true` convierte una respuesta fallida en el camino de excepción que espera nuestra página. El resultado exitoso contiene `data` además de metadatos HTTP.

En `App.tsx`, importa `toProblem` de `./api/problem` y sustituye las dos conversiones `error instanceof Error ? error.message : "Error inesperado"` por `toProblem(error).detail ?? toProblem(error).title`. El cliente puede lanzar un objeto ProblemDetail que no sea una instancia de `Error`.

## Comprobación

La aplicación sigue creando, listando, editando y borrando. Un título vacío produce un error legible. Cambia temporalmente el nombre de un campo en Java, regenera contrato y cliente, y observa qué consumidores señala TypeScript; después restaura el cambio y regenera.

Versiona `backend/openapi.json`, el cliente generado y su configuración junto al cambio de API. La razón registrada para elegir esta versión de Hey API está en el commit `6cbed03` y en el README del proyecto; no presupone el estado actual de otros generadores.

[Anterior](11-tests-backend.md) · [Índice](README.md) · [Siguiente: TanStack Query](13-tanstack-query.md)
