package com.example.todo.todos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTodoRequest(@NotBlank @Size(max = 200) String title) {}
