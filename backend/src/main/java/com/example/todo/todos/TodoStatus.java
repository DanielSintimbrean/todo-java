package com.example.todo.todos;

/** Filtro de {@code GET /api/todos?status=...}. Sin parámetro se devuelven todos. */
public enum TodoStatus {
    ACTIVE,
    COMPLETED
}
