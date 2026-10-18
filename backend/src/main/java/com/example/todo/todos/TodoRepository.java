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
