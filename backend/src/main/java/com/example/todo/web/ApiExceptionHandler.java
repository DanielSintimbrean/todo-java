package com.example.todo.web;

import com.example.todo.todos.TodoNotFoundException;
import com.example.todo.todos.TodoVersionConflictException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte excepciones en respuestas {@code application/problem+json} (RFC 9457), como un
 * middleware de excepciones en ASP.NET Core.
 *
 * <p>Heredar de {@link ResponseEntityExceptionHandler} hace que los errores propios de Spring MVC
 * (JSON mal formado, UUID inválido, método no permitido...) también salgan como ProblemDetail.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail handleNotFound(TodoNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Todo not found");
        return problem;
    }

    /** El cliente mandó una versión antigua (comprobado a mano en el service). */
    @ExceptionHandler
    ProblemDetail handleVersionConflict(TodoVersionConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Version conflict");
        problem.setProperty("currentVersion", ex.getCurrentVersion());
        return problem;
    }

    /** Dos transacciones modificaron la misma fila a la vez y @Version lo detectó al hacer el UPDATE. */
    @ExceptionHandler
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The todo was modified concurrently, reload and try again");
        problem.setTitle("Version conflict");
        return problem;
    }

    /** Añade al ProblemDetail estándar un mapa {@code errors} campo → mensaje para mostrarlo en la UI. */
    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getFieldErrors()) {
            errors.putIfAbsent(error.getField(), Objects.requireNonNullElse(error.getDefaultMessage(), "is invalid"));
        }
        ProblemDetail problem = ex.getBody();
        problem.setDetail("Validation failed");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
