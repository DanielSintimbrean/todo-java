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
