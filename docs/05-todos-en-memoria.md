# 05. Primeros TODOs en memoria y records de Java

Ya sabes devolver texto. Ahora devolverás objetos JSON y recibirás datos del navegador. Guardaremos las tareas en memoria para aprender HTTP y Java antes de configurar una base de datos.

## 1. Define los datos de entrada y salida

Crea el paquete `com.example.todo.todos`, es decir, la carpeta `backend/src/main/java/com/example/todo/todos/`.

Crea `CreateTodoRequest.java` dentro:

```java
package com.example.todo.todos;

public record CreateTodoRequest(String title) {}
```

Crea `TodoResponse.java` en el mismo paquete:

```java
package com.example.todo.todos;

import java.util.UUID;

public record TodoResponse(UUID id, String title, boolean completed) {}
```

Un `record` declara datos y genera constructor, métodos de lectura, `equals`, `hashCode` y `toString`. En Java lees `request.title()`, sin prefijo `get`. Los campos de un record no se pueden reasignar después de construirlo. Esto no hace inmutables automáticamente los objetos que pudiera contener.

`String` representa texto, `boolean` admite `true` o `false` y `UUID` identifica una tarea. Al serializar JSON, el UUID viajará como texto.

Usamos tipos distintos para entrada y salida porque el usuario solo debe enviar el título. El servidor decide el identificador y el estado inicial.

## 2. Crea un controlador temporal

Crea `TodoController.java` en ese paquete:

```java
package com.example.todo.todos;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/todos")
public class TodoController {
    private final List<TodoResponse> todos = new CopyOnWriteArrayList<>();

    @GetMapping
    public List<TodoResponse> list() {
        return List.copyOf(todos);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse create(@RequestBody CreateTodoRequest request) {
        TodoResponse todo = new TodoResponse(UUID.randomUUID(), request.title(), false);
        todos.add(todo);
        return todo;
    }
}
```

`List<TodoResponse>` es una colección cuyos elementos son `TodoResponse`. El tipo entre `<...>` es un parámetro genérico. `new` crea un objeto y `final` impide reasignar la referencia `todos`; no impide añadir elementos a la colección.

Spring crea normalmente una sola instancia del controlador y puede atender varias peticiones a la vez. `CopyOnWriteArrayList` evita usar una `ArrayList` compartida sin protección en este ejemplo pequeño. No es una base de datos ni una solución para grandes volúmenes. `List.copyOf` devuelve una copia no modificable para esa respuesta.

`@RequestMapping` establece el prefijo. Las dos anotaciones sin ruta usan `/api/todos`, pero una responde a GET y la otra a POST. `@RequestBody` hace que Spring convierta el JSON de entrada a `CreateTodoRequest`.

Al devolver un record o una lista, Spring escribe JSON. El estado 201 comunica que el POST ha creado un recurso.

## 3. Prueba sin frontend

Reinicia Java y ejecuta:

```sh
curl -i http://localhost:8080/api/todos
curl -i -X POST http://localhost:8080/api/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"Aprender records"}'
curl -i http://localhost:8080/api/todos
```

El primer GET devuelve `[]`. El POST devuelve 201 y un objeto parecido a:

```json
{"id":"6b48011f-fab0-4ded-b18a-52621cf0ea32","title":"Aprender records","completed":false}
```

El último GET devuelve una lista que contiene ese objeto. El identificador real será distinto.

## 4. Observa las limitaciones

Crea dos tareas y reinicia Java. La lista vuelve a estar vacía porque los datos vivían en la memoria del proceso.

Envía también `{"title":""}`. Se acepta. Todavía no hemos pedido a Spring que valide el contenido. Arreglaremos eso después de crear el formulario, para que puedas ver cómo se presenta un error real al usuario.

## Comprobación

Puedes crear y listar tareas con `curl`, y entiendes por qué el reinicio las elimina. El `HelloController` puede permanecer durante el aprendizaje; lo retiraremos al terminar.

[Anterior](04-primera-request.md) · [Índice](README.md) · [Siguiente: tareas desde React](06-todos-desde-react.md)
