# 08. PostgreSQL, Flyway, JPA y transacciones

Vamos a sustituir la lista en memoria por una tabla. El frontend seguirá usando exactamente las mismas URLs. Primero incorporaremos las dependencias, después crearemos la tabla y por último conectaremos las clases Java.

## 1. Prepara PostgreSQL

Instala Docker Engine con Compose o Docker Desktop, según tu sistema. Comprueba que el motor está arrancado:

```sh
docker version
docker compose version
```

Crea `compose.yaml` en la raíz con este contenido. Son credenciales de desarrollo local.

### `compose.yaml`

```yaml
# Postgres para desarrollo local. No hace falta levantarlo a mano:
# spring-boot-docker-compose lo arranca al ejecutar el backend (ver application.properties).
services:
  postgres:
    image: postgres:18
    environment:
      POSTGRES_DB: todo
      POSTGRES_USER: todo
      POSTGRES_PASSWORD: todo
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql

volumes:
  postgres-data:
```

PostgreSQL es el proceso que almacena los datos. Docker lo ejecuta en un contenedor. El volumen `postgres-data` conserva los datos cuando el contenedor se detiene. En PostgreSQL 18 este montaje se hace en `/var/lib/postgresql`.

## 2. Añade las dependencias

Conserva los starters web y validation del POM. Añade dentro de `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.jspecify</groupId>
    <artifactId>jspecify</artifactId>
</dependency>
```

JPA define cómo representar objetos persistidos. Hibernate es la implementación que usa este proyecto. Spring Data JPA genera repositorios sobre esa implementación. El driver PostgreSQL abre la conexión y Flyway aplica los cambios de esquema.

`runtime` indica que esas bibliotecas son necesarias al ejecutar. JSpecify aporta anotaciones de nulabilidad; las usaremos en los campos que Hibernate rellena después de crear un objeto.

## 3. Configura el arranque local

Reemplaza `backend/src/main/resources/application.properties`:

```properties
spring.application.name=todo
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.docker.compose.file=../compose.yaml
spring.docker.compose.lifecycle-management=start-only
```

Arranca siempre Maven desde `backend/` para que `../compose.yaml` se resuelva correctamente. La integración Docker Compose de Spring Boot levanta el servicio y obtiene sus credenciales. No necesitas declarar además `spring.datasource.url` en este recorrido.

Flyway creará el esquema. `ddl-auto=validate` pide a Hibernate que compruebe que las tablas coinciden con las entidades, sin crearlas ni modificarlas. `open-in-view=false` limita el acceso a datos al trabajo transaccional del servicio.

`start-only` deja PostgreSQL encendido cuando paras Java. Si tienes otro proceso usando 5432, libéralo o cambia el puerto publicado de Compose, por ejemplo a `5433:5432`; conserva el puerto interno 5432.

## 4. Escribe la primera migración

Crea la carpeta `backend/src/main/resources/db/migration/` y el siguiente fichero:

### `backend/src/main/resources/db/migration/V1__create_todo.sql`

```sql
create table todo (
    id         uuid         primary key,
    title      varchar(200) not null,
    completed  boolean      not null default false,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    version    bigint       not null
);

create index todo_created_at_idx on todo (created_at);
```

El nombre tiene dos guiones bajos entre `V1` y la descripción. Flyway registra qué migraciones ha ejecutado y comprueba su contenido. Una vez aplicada V1, añade otra migración para un cambio posterior en lugar de editar V1.

`uuid` guarda el identificador. `timestamptz` representa instantes y `version` nos permitirá detectar escrituras concurrentes. Creamos ya el esquema final para no introducir otra migración mientras aprendemos JPA. Explicaremos el protocolo de versiones del cliente en el capítulo 10.

## 5. Crea la entidad

Un DTO representa datos que viajan por HTTP. Una entidad representa una fila que Hibernate puede cargar y modificar. Conserva `TodoResponse` como DTO y crea `Todo.java`:

### `backend/src/main/java/com/example/todo/todos/Todo.java`

```java
package com.example.todo.todos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Entidad JPA. No sale nunca del service: la API trabaja con {@link TodoResponse}.
 *
 * <p>No hay setters públicos: el estado solo cambia mediante métodos con intención
 * ({@link #rename}, {@link #toggle}...). Hibernate detecta esos cambios solo (dirty checking).
 */
@Entity
public class Todo {

    // Los campos son @Nullable porque Hibernate los rellena al persistir, no en el constructor.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private @Nullable UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @Column(nullable = false)
    private @Nullable Instant updatedAt;

    // Concurrencia optimista: Hibernate añade "where version = ?" a cada UPDATE y lo incrementa.
    // Si otra transacción lo cambió antes, lanza OptimisticLockException (ver ApiExceptionHandler).
    @Version
    private @Nullable Long version;

    /** Constructor que exige JPA. No lo uses en tu código. */
    protected Todo() {
        this.title = "";
    }

    public Todo(String title) {
        this.title = title.strip();
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = now();
    }

    // Postgres guarda microsegundos y Instant.now() trae nanosegundos: sin truncar, la respuesta
    // de un POST/PATCH tendría más precisión que la misma fecha leída después con un GET.
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public void rename(String title) {
        this.title = title.strip();
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void toggle() {
        completed = !completed;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public @Nullable Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Long getVersion() {
        return version;
    }
}
```

`@Entity` registra el objeto persistido. `@Id` identifica la clave primaria y `@GeneratedValue` pide a Hibernate generar el UUID. La estrategia de nombres de Spring Boot relaciona `createdAt` con `created_at`.

JPA necesita el constructor sin argumentos, que aquí es `protected`. Tu código usará el constructor público con título. Los campos de identificación, fecha y versión pueden ser `null` antes de persistir, por eso son tipos de referencia y llevan `@Nullable`. El estado `completed` empieza en `false` por ser un booleano primitivo.

`@PrePersist` se ejecuta antes de insertar y `@PreUpdate` antes de escribir cambios. Truncamos a microsegundos porque PostgreSQL conserva esa precisión; así una respuesta de creación y un GET posterior no difieren solo por nanosegundos.

`@Version` ya permitirá a Hibernate comprobar la versión al escribir. Todavía no enviaremos versiones en los cuerpos de petición.

## 6. Amplía la respuesta y añade el repositorio

Reemplaza `TodoResponse.java` por la versión siguiente. Las fechas viajarán como cadenas ISO-8601 y la versión como un número JSON.

### `backend/src/main/java/com/example/todo/todos/TodoResponse.java`

```java
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
```

`TodoResponse.from` solo acepta una entidad ya persistida. `requireNonNull` falla si los datos que deberían estar asignados no lo están. Las anotaciones `@NotNull` de este DTO servirán para el contrato OpenAPI; aquí no estamos activando validación automática de las respuestas.

Crea estos dos ficheros. Usaremos el filtro en el siguiente capítulo:

### `backend/src/main/java/com/example/todo/todos/TodoStatus.java`

```java
package com.example.todo.todos;

/** Filtro de {@code GET /api/todos?status=...}. Sin parámetro se devuelven todos. */
public enum TodoStatus {
    ACTIVE,
    COMPLETED
}
```

### `backend/src/main/java/com/example/todo/todos/TodoRepository.java`

```java
package com.example.todo.todos;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data genera la implementación en tiempo de arranque. Las consultas salen del nombre del método
 * (sería el equivalente a escribir {@code db.Todos.Where(...).OrderBy(...)} en EF Core).
 */
public interface TodoRepository extends JpaRepository<Todo, UUID> {

    List<Todo> findAllByOrderByCreatedAtAsc();

    List<Todo> findByCompletedOrderByCreatedAtAsc(boolean completed);
}
```

La interfaz hereda operaciones como `save` y `findById`. Spring crea su implementación al arrancar. El nombre `findByCompletedOrderByCreatedAtAsc` pide filtrar por `completed` y ordenar por fecha ascendente.

## 7. Introduce el servicio

Crea `backend/src/main/java/com/example/todo/todos/TodoService.java`:

```java
package com.example.todo.todos;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TodoService {
    private final TodoRepository todos;

    public TodoService(TodoRepository todos) {
        this.todos = todos;
    }

    @Transactional(readOnly = true)
    public List<TodoResponse> list() {
        return todos.findAllByOrderByCreatedAtAsc().stream()
                .map(TodoResponse::from).toList();
    }

    @Transactional
    public TodoResponse create(CreateTodoRequest request) {
        Todo todo = todos.save(new Todo(request.title()));
        return TodoResponse.from(todo);
    }
}
```

`@Service` permite que Spring cree esta clase. El constructor declara que necesita un repositorio. Spring lo proporciona, sin que escribas `new TodoRepository()`. Es inyección de dependencias por constructor.

`@Transactional` delimita la unidad de trabajo. Spring abre la transacción antes de ejecutar el método y la confirma cuando termina correctamente. Una excepción de ejecución normalmente provoca rollback. El DTO se construye dentro de esa transacción.

`stream().map(TodoResponse::from).toList()` transforma cada entidad en una respuesta. La referencia de método equivale a `todo -> TodoResponse.from(todo)`.

## 8. Sustituye el controlador en memoria

Reemplaza `TodoController.java` por:

```java
package com.example.todo.todos;

import jakarta.validation.Valid;
import java.util.List;
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
    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public List<TodoResponse> list() {
        return todoService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TodoResponse create(@Valid @RequestBody CreateTodoRequest request) {
        return todoService.create(request);
    }
}
```

El controlador traduce HTTP a llamadas del servicio. El servicio coordina la operación y el repositorio accede a datos. Ya no hay ninguna colección compartida en el controlador.

En el tipo `Todo` del frontend añade `createdAt: string`, `updatedAt: string` y `version: number`. El resto de la página continúa funcionando porque no hemos cambiado las URLs ni los campos anteriores.

## Comprobación

Arranca Java desde `backend/`. Debes ver que Compose levanta PostgreSQL y que Flyway aplica V1. Crea una tarea, detén Java, vuelve a arrancarlo y lista las tareas. Ahora la tarea permanece.

Desde la raíz también puedes inspeccionar los datos:

```sh
docker compose exec postgres psql -U todo -d todo -c 'select id, title, completed, version from todo;'
docker compose down
```

`down` detiene el contenedor y conserva el volumen. Arranca Java otra vez para continuar. Si Flyway falla, lee primero el mensaje SQL; si Hibernate indica una columna ausente, compara entidad y migración.

[Anterior](07-validacion-y-errores.md) · [Índice](README.md) · [Siguiente: CRUD y filtros](09-crud-y-filtros.md)
