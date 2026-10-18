package com.example.todo.todos;

import java.util.UUID;

public class TodoVersionConflictException extends RuntimeException {

    private final long currentVersion;

    public TodoVersionConflictException(UUID id, long currentVersion) {
        super("Todo " + id + " was modified by someone else (current version is " + currentVersion + ")");
        this.currentVersion = currentVersion;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
