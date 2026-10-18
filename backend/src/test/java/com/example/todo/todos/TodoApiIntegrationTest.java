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

        client.delete().uri("/api/todos/{id}", created.id()).exchange().expectStatus().isNoContent();

        client.get().uri("/api/todos/{id}", created.id()).exchange().expectStatus().isNotFound();
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
