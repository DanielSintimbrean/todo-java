# todo-java

Proyecto de aprendizaje de Java moderno: TODO app full-stack.

- **Backend:** Java 25 + Spring Boot 4 (Spring MVC, virtual threads), Maven, Spring Data JPA, Flyway, PostgreSQL
- **Contrato:** springdoc → `backend/openapi.json` (versionado) → openapi-typescript
- **Frontend:** React + TypeScript + Vite+ (`vp`), TanStack Router + Query, openapi-fetch + openapi-react-query, Tailwind + shadcn/ui
- **Tooling:** mise + pnpm

## Arrancar

Requisitos: [mise](https://mise.jdx.dev) y Docker (Postgres en local y Testcontainers en los tests).

```sh
mise install      # Java 25 (Temurin), Node LTS y pnpm
mise run dev      # backend en :8080 + frontend en :5173
```

Abre http://localhost:5173. No hace falta levantar la base de datos: al arrancar, el backend ejecuta
`docker compose up` sobre [`compose.yaml`](compose.yaml) (`spring-boot-docker-compose`) y se configura
solo con esas credenciales. El contenedor se queda encendido entre reinicios; `mise run db:down` lo para.

Swagger UI: http://localhost:8080/swagger-ui.html

| Tarea | Qué hace |
| --- | --- |
| `mise run dev` | Backend y frontend en paralelo (`dev:backend` y `dev:frontend` por separado) |
| `mise run test` | Tests de backend (JUnit + Testcontainers) y frontend (`vp test`) |
| `mise run api:generate` | Regenera `backend/openapi.json` desde un test y `frontend/src/api/schema.d.ts` a partir de él |
| `mise run check` | Lo que correría CI: `mvnw verify`, `vp check`, `vp test`, `vp build` y `git diff --exit-code` sobre los ficheros generados |

Dentro del repo, `mise` pone `frontend/node_modules/.bin` en el `PATH`, así que `vp` es siempre el
vite-plus local del proyecto (versión fijada en `frontend/pnpm-workspace.yaml`).

## Estructura

```
backend/src/main/java/com/example/todo/
  todos/     entidad, repositorio, service, controller, DTOs (records) y excepciones
  web/       @RestControllerAdvice (ProblemDetail) y configuración de OpenAPI
backend/src/main/resources/db/migration/   migraciones de Flyway (SQL a mano)
backend/openapi.json                       contrato del API, generado por OpenApiDocumentTest
frontend/src/
  api/       cliente tipado ($api), hooks de mutación y schema.d.ts generado
  routes/    rutas de TanStack Router (file-based)
  components/ componentes de la app y, en ui/, los de shadcn
```

### Cambiar el API

1. Cambia el backend (controller, DTOs...).
2. `mise run api:generate`: el test [`OpenApiDocumentTest`](backend/src/test/java/com/example/todo/OpenApiDocumentTest.java)
   arranca la app contra un Postgres de Testcontainers, pide `/v3/api-docs` y escribe `openapi.json`.
   Después, openapi-typescript genera los tipos del frontend.
3. TypeScript te dirá qué parte del frontend se ha roto. Commitea `openapi.json` y `schema.d.ts` con el cambio:
   `mise run check` falla si no coinciden con el código.

### Decisiones

- **API:** `GET/POST /api/todos` (`?status=ACTIVE|COMPLETED` opcional), `GET/PATCH/DELETE /api/todos/{id}`
  y `POST /api/todos/{id}/toggle`. Se usa **PATCH** para actualizar: los campos que no se envían no cambian, y
  `version` es obligatoria. Si no coincide con la actual, la respuesta es 409.
- **Errores:** todos son `application/problem+json` (RFC 9457). Los 400 de validación añaden `errors`
  (campo → mensaje) y los 409, `currentVersion`. El esquema automático que springdoc genera para
  `ProblemDetail` no coincide con el JSON real, así que se documenta con el record
  [`ApiProblem`](backend/src/main/java/com/example/todo/web/ApiProblem.java).
- **Versiones:** Spring Boot 4.1.1 con springdoc-openapi 3.1.1 (la línea 3.x es la de Boot 4; la 3.1 está
  construida contra Boot 4.1).
- **JUnit:** Spring Boot 4.1 gestiona JUnit **6** (Jupiter), no JUnit 5. La API de Jupiter es la misma
  (`@Test`, `@BeforeEach`...), así que se usa la versión que trae Boot en vez de forzar una anterior.

## Notas para quien viene de C#/EF

**Dirty checking frente a `SaveChanges()`.** En EF Core cambias una entidad y llamas a `SaveChanges()`.
Con JPA no hay "save" para actualizar: una entidad cargada dentro de una transacción está *managed*, y al
hacer commit Hibernate compara su estado con el original y lanza los `UPDATE` necesarios. Fíjate en que
[`TodoService.update`](backend/src/main/java/com/example/todo/todos/TodoService.java#L47) no llama a
`save()`. El `flush()` de la línea 68 solo adelanta el `UPDATE` para que la respuesta lleve la versión
nueva. `save()` solo se usa para insertar (`create`).

**`@Transactional`** ([`TodoService`](backend/src/main/java/com/example/todo/todos/TodoService.java#L41)) define
dónde empieza y acaba la unidad de trabajo (el equivalente a la vida del `DbContext` más `SaveChanges`). Spring
envuelve el bean en un proxy que abre la transacción al entrar en el método y hace commit al salir, o rollback si
sale una `RuntimeException`. Ojo: los beans de Spring son **singletons** por defecto, no *scoped* como un
`DbContext`. El `EntityManager` que usa el repositorio es un proxy ligado a la transacción en curso. Con
`spring.jpa.open-in-view=false` no hay lazy loading fuera del service.

**`@Version`** ([`Todo.java`](backend/src/main/java/com/example/todo/todos/Todo.java#L43)) es el
`[ConcurrencyCheck]`/`rowversion` de EF. Hibernate añade `where version = ?` a cada `UPDATE`. Si otra transacción
ganó la carrera, lanza una excepción que
[`ApiExceptionHandler`](backend/src/main/java/com/example/todo/web/ApiExceptionHandler.java#L50) convierte en
409. Además, el cliente manda la versión que vio y el service la compara
([línea 52](backend/src/main/java/com/example/todo/todos/TodoService.java#L52)), así no se pisan cambios
hechos entre que cargó la página y guardó.

**Records** ([`TodoResponse`](backend/src/main/java/com/example/todo/todos/TodoResponse.java),
[`UpdateTodoRequest`](backend/src/main/java/com/example/todo/todos/UpdateTodoRequest.java)) son como los
`record` de C#: inmutables, con constructor, accessors (`todo.title()`, sin `get`), `equals` y `toString`.
Son los DTOs. La entidad es una clase normal, porque JPA necesita un constructor vacío y campos mutables, y nunca
sale del service.

**`sealed` + pattern matching.** [`UpdateResult`](backend/src/main/java/com/example/todo/todos/UpdateResult.java)
es una interfaz sellada con dos records. El `switch` del
[controller](backend/src/main/java/com/example/todo/todos/TodoController.java#L83) los desestructura
(`case Updated(TodoResponse todo)`) y el compilador exige que sea exhaustivo: si añades un caso, no compila hasta
que lo trates. Es como un `switch` sobre una jerarquía cerrada en C#, pero comprobado. Otro ejemplo pequeño:
`switch (status)` con `case null` en [`TodoService.list`](backend/src/main/java/com/example/todo/todos/TodoService.java#L28).

**Virtual threads** ([`application.properties`](backend/src/main/resources/application.properties#L4)). En
C# escribes `async`/`await` para no bloquear hilos. En Java 21+ escribes código bloqueante normal (JDBC,
`RestClient`...) y cada request corre en un *virtual thread*: cuando se bloquea en I/O, la JVM lo desmonta del hilo
real y lo reanuda después. Escala parecido sin "colorear" las funciones con `async`. Por eso aquí se usa Spring MVC
y no WebFlux.

**Otros equivalentes rápidos:**
- Nullability: `@NullMarked` en cada `package-info.java` es como `<Nullable>enable</Nullable>`; lo nullable
  se marca con `@Nullable` (JSpecify). `javac` no lo comprueba: lo hacen IntelliJ o herramientas como NullAway.
- Migraciones: en lugar de `dotnet ef migrations add`, Flyway ejecuta SQL escrito a mano
  ([`V1__create_todo.sql`](backend/src/main/resources/db/migration/V1__create_todo.sql)) y Hibernate solo
  valida (`ddl-auto=validate`).
- Repositorios: Spring Data genera las consultas a partir del nombre del método
  ([`TodoRepository`](backend/src/main/java/com/example/todo/todos/TodoRepository.java)), lo que en EF harías
  con LINQ.
- Tests de integración: `@SpringBootTest` + Testcontainers con `@ServiceConnection`
  ([`TestcontainersConfiguration`](backend/src/test/java/com/example/todo/TestcontainersConfiguration.java))
  es el equivalente a `WebApplicationFactory` + Testcontainers for .NET.
