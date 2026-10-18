# 07. Validación y respuestas de error

El backend debe rechazar un título vacío aunque la petición no venga de nuestra página. Añadiremos validación en Java y un formato de error que el frontend pueda mostrar.

## 1. Añade validación al backend

Dentro de `<dependencies>` en `backend/pom.xml`, añade:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

Reemplaza `CreateTodoRequest.java`:

```java
package com.example.todo.todos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTodoRequest(@NotBlank @Size(max = 200) String title) {}
```

En `TodoController`, importa `jakarta.validation.Valid` y cambia la firma de creación:

```java
public TodoResponse create(@Valid @RequestBody CreateTodoRequest request) {
```

Conserva el cuerpo y sus anotaciones de POST y estado 201. `@NotBlank` rechaza `null`, texto vacío y texto compuesto solo de espacios. `@Size` limita la longitud. `@Valid` activa la validación de ese argumento antes de entrar al método.

En la construcción de la respuesta, usa ahora `request.title().strip()` en lugar de `request.title()`. Validar y normalizar son pasos distintos. La validación rechaza entradas incorrectas y `strip()` elimina espacios en los extremos de las válidas.

## 2. Convierte errores a ProblemDetail

Crea `backend/src/main/java/com/example/todo/web/ApiExceptionHandler.java`:

```java
package com.example.todo.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            errors.putIfAbsent(error.getField(),
                    Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"));
        }
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
```

`@RestControllerAdvice` aplica este tratamiento a los controladores. La clase base ya trata errores de Spring MVC, como un JSON mal formado. Aquí especializamos el error de validación para incluir mensajes por campo.

`Map<String, String>` relaciona nombres de campo con mensajes. El bucle recorre los errores y `putIfAbsent` conserva el primero de cada campo. `Objects.requireNonNullElse` proporciona un mensaje si el validador no trae uno.

`ProblemDetail` representa un problema HTTP. Sus extensiones, como `errors`, aparecen al mismo nivel que `status` o `title` en el JSON.

## 3. Comprueba el error HTTP

Reinicia Java y ejecuta:

```sh
curl -i -X POST http://localhost:8080/api/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"   "}'
```

Debes obtener 400 y `Content-Type: application/problem+json`. El cuerpo tendrá una estructura parecida a esta; el mensaje concreto puede variar por idioma:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/todos",
  "errors": {"title": "must not be blank"}
}
```

## 4. Lee el detalle en el frontend

En `frontend/src/api/todos.ts`, reemplaza solo `requestJson` por:

```ts
export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    let message = `Error HTTP ${response.status}`;
    try {
      const problem = await response.json() as {
        detail?: string;
        errors?: Record<string, string>;
      };
      message = Object.values(problem.errors ?? {}).join(", ") || problem.detail || message;
    } catch {
      // Un proxy puede devolver texto o un cuerpo vacío.
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}
```

Envía un título vacío desde el formulario. No añadas todavía `required` al input: queremos observar el error del backend. La aplicación final también permite ver esa validación del servidor.

Este lector es una primera versión. En el capítulo 12 conservaremos el objeto completo de error y trataremos también fallos de red mediante `toProblem`.

## Comprobación

Un título válido se guarda sin espacios en los extremos. Un título vacío devuelve 400 y no crea ninguna tarea. El frontend muestra el mensaje. Prueba también un cuerpo mal formado para distinguir un error de sintaxis JSON de uno de validación.

[Anterior](06-todos-desde-react.md) · [Índice](README.md) · [Siguiente: persistencia](08-persistencia.md)
