# 11. Tests de integración con PostgreSQL real

Ya has probado los casos con `curl`. Vamos a automatizarlos ejecutando la aplicación y una base PostgreSQL temporal. Así comprobaremos también migraciones, serialización y consultas reales.

## 1. Añade las dependencias de test

Dentro de `<dependencies>` en el POM añade:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

El parent de Spring Boot fija sus versiones compatibles. Este proyecto usa JUnit Jupiter con la versión gestionada por Boot 4.1, que en este checkout es JUnit 6. No fuerces JUnit 5 siguiendo un tutorial antiguo.

## 2. Configura la base de datos de test

Crea estos ficheros bajo `src/test/java`, no en `src/main/java`:

### `backend/src/test/java/com/example/todo/TestcontainersConfiguration.java`

```java
package com.example.todo;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres real en un contenedor para los tests. {@code @ServiceConnection} hace que Spring Boot
 * configure el DataSource con la URL y credenciales del contenedor, sin tocar properties.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:18");
    }
}
```

### `backend/src/test/java/com/example/todo/IntegrationTest.java`

```java
package com.example.todo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Arranca la app entera en un puerto aleatorio contra el Postgres de Testcontainers.
 * Al compartir anotación, todos los tests reutilizan el mismo contexto de Spring y el mismo contenedor.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {}
```

`@ServiceConnection` configura la conexión a partir del contenedor. Su puerto y credenciales no dependen del PostgreSQL de desarrollo. `RANDOM_PORT` permite arrancar un servidor HTTP sin reservar 8080.

`@IntegrationTest` es una anotación propia que reúne las otras anotaciones. `@Retention(RUNTIME)` permite leerla al ejecutar y `@Target(TYPE)` limita su uso a tipos. Las clases que comparten esta configuración pueden reutilizar el contexto de Spring y el contenedor.

## 3. Automatiza primero creación y lectura

Crea `TodoApiIntegrationTest.java` con el fichero completo de abajo, pero empieza ejecutando solo `createsAndGetsTodo`. Lee sus tres pasos: crear, pedir por identificador y comparar.

`@BeforeEach` vacía la tabla antes de cada test. Estas pruebas no deben ejecutarse en paralelo entre sí porque comparten esa tabla. `RestTestClient` hace peticiones a la aplicación arrancada. AssertJ permite expresar las comprobaciones sobre el objeto recibido.

## 4. Añade los demás casos

Continúa con lista y filtros, actualización, conflicto, toggle, borrado, 404 y validación. Cada test fija un comportamiento observable de la API.

### `backend/src/test/java/com/example/todo/todos/TodoApiIntegrationTest.java`

```java
package com.example.todo.todos;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.todo.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@IntegrationTest
class TodoApiIntegrationTest {

    @Autowired
    RestTestClient client;

    @Autowired
    TodoRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsAndGetsTodo() {
        TodoResponse created = create("  Buy milk  ");

        assertThat(created.title()).isEqualTo("Buy milk");
        assertThat(created.completed()).isFalse();
        assertThat(created.version()).isZero();

        TodoResponse fetched = client.get()
                .uri("/api/todos/{id}", created.id())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TodoResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(fetched).isEqualTo(created);
    }

    @Test
    void listsTodosInCreationOrderAndFiltersByStatus() {
        TodoResponse first = create("First");
        TodoResponse second = create("Second");
        toggle(second.id());

        assertThat(list("")).extracting(TodoResponse::title).containsExactly("First", "Second");
        assertThat(list("?status=ACTIVE")).extracting(TodoResponse::id).containsExactly(first.id());
        assertThat(list("?status=COMPLETED")).extracting(TodoResponse::id).containsExactly(second.id());
    }

    @Test
    void updatesTitleAndBumpsVersion() {
        TodoResponse created = create("Old title");

        TodoResponse updated = client.patch()
                .uri("/api/todos/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "New title", "version", created.version()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TodoResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(updated.title()).isEqualTo("New title");
        assertThat(updated.completed()).isFalse();
        assertThat(updated.version()).isEqualTo(created.version() + 1);
        assertThat(updated.updatedAt()).isAfter(created.updatedAt());
    }

    @Test
    void rejectsUpdateWithStaleVersion() {
        TodoResponse created = create("Shared todo");
        toggle(created.id()); // ahora la versión es 1

        client.patch()
                .uri("/api/todos/{id}", created.id())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "Overwrite", "version", created.version()))
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title")
                .isEqualTo("Version conflict")
                .jsonPath("$.currentVersion")
                .isEqualTo(1);
    }

    @Test
    void togglesCompleted() {
        TodoResponse created = create("Toggle me");

        assertThat(toggle(created.id()).completed()).isTrue();
        assertThat(toggle(created.id()).completed()).isFalse();
    }

    @Test
    void deletesTodo() {
        TodoResponse created = create("Delete me");

        client.delete()
                .uri("/api/todos/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNoContent();

        client.get()
                .uri("/api/todos/{id}", created.id())
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    @Test
    void returnsProblemDetailWhenTodoDoesNotExist() {
        client.get()
                .uri("/api/todos/{id}", UUID.randomUUID())
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(404)
                .jsonPath("$.title")
                .isEqualTo("Todo not found");
    }

    @Test
    void rejectsBlankTitleWithValidationErrors() {
        client.post()
                .uri("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("title", "   "))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo(400)
                // El texto del mensaje depende del Accept-Language, así que solo comprobamos que está.
                .jsonPath("$.errors.title")
                .isNotEmpty();

        assertThat(repository.count()).isZero();
    }

    private TodoResponse create(String title) {
        return client.post()
                .uri("/api/todos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateTodoRequest(title))
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(TodoResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private TodoResponse toggle(UUID id) {
        return client.post()
                .uri("/api/todos/{id}/toggle", id)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(TodoResponse.class)
                .returnResult()
                .getResponseBody();
    }

    private List<TodoResponse> list(String query) {
        return client.get()
                .uri("/api/todos" + query)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(new ParameterizedTypeReference<List<TodoResponse>>() {})
                .returnResult()
                .getResponseBody();
    }
}
```

El conflicto se provoca enviando una versión antigua después de alternar la tarea. No dependemos de que dos hilos corran exactamente a la vez para reproducirlo. Ese test comprueba el protocolo de versión antigua; no demuestra por sí solo todas las carreras de escritura posibles.

La igualdad entre el POST y el GET también comprueba que las fechas mantienen la precisión. El test del título vacío comprueba tanto el error HTTP como que no haya una fila insertada.

## 5. Ejecuta los tests

Docker debe estar disponible. Desde la raíz:

```sh
cd backend
mise exec -- ./mvnw -Dtest=TodoApiIntegrationTest#createsAndGetsTodo test
mise exec -- ./mvnw test
```

No necesitas tener abierto el backend de desarrollo. Testcontainers arranca su propio PostgreSQL temporal y Spring configura su conexión. La primera ejecución puede tardar al descargar la imagen.

## Comprobación

Los ocho tests del fichero pasan. Cambia temporalmente una expectativa de título para ver un fallo y vuelve a dejarla como estaba. El informe debe señalar qué valor recibió y cuál esperaba.

Si aparece un error de Docker, resuelve el acceso al motor antes de cambiar el código de negocio. Si falla Flyway, el test puede haber descubierto un problema de esquema real.

[Anterior](10-concurrencia-y-java-moderno.md) · [Índice](README.md) · [Siguiente: OpenAPI y cliente generado](12-openapi-y-cliente.md)
