package com.example.todo.todos;

import java.util.UUID;

public class TodoNotFoundException extends RuntimeException {

    public TodoNotFoundException(UUID id) {
        super("Todo " + id + " not found");
    }
}
