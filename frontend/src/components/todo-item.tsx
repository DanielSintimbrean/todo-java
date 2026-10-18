import { PencilIcon, Trash2Icon } from "lucide-react";
import { type FormEvent, useState } from "react";
import type { Todo } from "@/api/client";
import { useDeleteTodo, useToggleTodo, useUpdateTodo } from "@/api/todos";
import { ProblemAlert } from "@/components/problem-alert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";

export function TodoItem({ todo }: { todo: Todo }) {
  const toggleTodo = useToggleTodo();
  const updateTodo = useUpdateTodo();
  const deleteTodo = useDeleteTodo();
  // null = no estamos editando el título.
  const [draft, setDraft] = useState<string | null>(null);

  const path = { id: todo.id };
  const error = toggleTodo.error ?? updateTodo.error ?? deleteTodo.error;

  function startEditing() {
    updateTodo.reset();
    setDraft(todo.title);
  }

  function cancelEditing() {
    updateTodo.reset();
    setDraft(null);
  }

  function save(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if (draft === null || updateTodo.isPending) {
      return;
    }
    if (draft === todo.title) {
      setDraft(null);
      return;
    }
    // Mandamos la versión que estamos viendo: si alguien lo cambió antes, el backend responde 409.
    updateTodo.mutate(
      { params: { path }, body: { title: draft, version: todo.version } },
      { onSuccess: () => setDraft(null) },
    );
  }

  return (
    <li className="flex flex-col gap-2 py-2">
      <div className="flex items-center gap-3">
        <Checkbox
          checked={todo.completed}
          onCheckedChange={() => toggleTodo.mutate({ params: { path } })}
          aria-label={`Marcar "${todo.title}" como ${todo.completed ? "pendiente" : "completado"}`}
        />
        {draft === null ? (
          <span
            onDoubleClick={startEditing}
            className={`flex-1 truncate ${todo.completed ? "text-muted-foreground line-through" : ""}`}
          >
            {todo.title}
          </span>
        ) : (
          <form onSubmit={save} className="flex-1">
            <Input
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              onBlur={() => save()}
              onKeyDown={(event) => event.key === "Escape" && cancelEditing()}
              disabled={updateTodo.isPending}
              aria-label="Título"
              autoFocus
            />
          </form>
        )}
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={startEditing}
          disabled={draft !== null}
          aria-label={`Editar "${todo.title}"`}
        >
          <PencilIcon />
        </Button>
        <Button
          variant="ghost"
          size="icon-sm"
          onClick={() => deleteTodo.mutate({ params: { path } })}
          disabled={deleteTodo.isPending}
          aria-label={`Borrar "${todo.title}"`}
        >
          <Trash2Icon />
        </Button>
      </div>
      {error && <ProblemAlert error={error} />}
    </li>
  );
}
