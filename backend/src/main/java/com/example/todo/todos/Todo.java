package com.example.todo.todos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Entidad JPA. No sale nunca del service: la API trabaja con {@link TodoResponse}.
 *
 * <p>No hay setters públicos: el estado solo cambia mediante métodos con intención
 * ({@link #rename}, {@link #toggle}...). Hibernate detecta esos cambios solo (dirty checking).
 */
@Entity
public class Todo {

    // Los campos son @Nullable porque Hibernate los rellena al persistir, no en el constructor.
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private @Nullable UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false, updatable = false)
    private @Nullable Instant createdAt;

    @Column(nullable = false)
    private @Nullable Instant updatedAt;

    // Concurrencia optimista: Hibernate añade "where version = ?" a cada UPDATE y lo incrementa.
    // Si otra transacción lo cambió antes, lanza OptimisticLockException (ver ApiExceptionHandler).
    @Version
    private @Nullable Long version;

    /** Constructor que exige JPA. No lo uses en tu código. */
    protected Todo() {
        this.title = "";
    }

    public Todo(String title) {
        this.title = title.strip();
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void rename(String title) {
        this.title = title.strip();
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public void toggle() {
        completed = !completed;
    }

    public @Nullable UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public @Nullable Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getUpdatedAt() {
        return updatedAt;
    }

    public @Nullable Long getVersion() {
        return version;
    }
}
