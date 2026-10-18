package com.example.todo.todos;

/**
 * Resultado de {@link TodoService#update}. Una interfaz {@code sealed} enumera todas sus implementaciones,
 * así que un {@code switch} sobre ella es exhaustivo: si mañana añades un caso, el controller deja de
 * compilar hasta que lo trates (ver {@link TodoController#update}).
 *
 * <p>Un conflicto de versión no es un error inesperado sino un resultado normal de la operación,
 * por eso se devuelve como valor en lugar de lanzarse como excepción.
 */
public sealed interface UpdateResult {

    record Updated(TodoResponse todo) implements UpdateResult {}

    record VersionConflict(long currentVersion) implements UpdateResult {}
}
