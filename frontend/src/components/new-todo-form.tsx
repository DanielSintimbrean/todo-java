import { PlusIcon } from "lucide-react";
import { type FormEvent, useState } from "react";
import { useCreateTodo } from "@/api/todos";
import { ProblemAlert } from "@/components/problem-alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";

export function NewTodoForm() {
  const [title, setTitle] = useState("");
  const createTodo = useCreateTodo();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // Sin validación en cliente a propósito: así se ve el ProblemDetail de validación del backend.
    createTodo.mutate({ body: { title } }, { onSuccess: () => setTitle("") });
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <div className="flex gap-2">
        <Input
          value={title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="¿Qué hay que hacer?"
          aria-label="Nuevo TODO"
        />
        <Button type="submit" disabled={createTodo.isPending}>
          {createTodo.isPending ? <Spinner /> : <PlusIcon />}
          Añadir
        </Button>
      </div>
      {createTodo.isError && <ProblemAlert error={createTodo.error} />}
    </form>
  );
}
