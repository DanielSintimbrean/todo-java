import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { TodoStatus } from "@/api/client";
import { listTodosOptions } from "@/api/generated/@tanstack/react-query.gen";
import { NewTodoForm } from "@/components/new-todo-form";
import { TodoItem } from "@/components/todo-item";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Spinner } from "@/components/ui/spinner";

type Filter = "active" | "completed";

// El filtro vive en la URL (?filter=active). Sin parámetro se muestran todos.
type TodoSearch = {
  filter?: Filter;
};

const statusByFilter: Record<Filter, TodoStatus> = {
  active: "ACTIVE",
  completed: "COMPLETED",
};

const filterLinks = [
  { filter: undefined, label: "Todos" },
  { filter: "active", label: "Activos" },
  { filter: "completed", label: "Completados" },
] as const;

// Mismos parámetros en el loader y en el componente, así comparten la entrada de caché.
function listOptions(filter: Filter | undefined) {
  return listTodosOptions({ query: { status: filter && statusByFilter[filter] } });
}

export const Route = createFileRoute("/")({
  validateSearch: (search: Record<string, unknown>): TodoSearch =>
    search.filter === "active" || search.filter === "completed" ? { filter: search.filter } : {},
  loaderDeps: ({ search }) => ({ filter: search.filter }),
  // Precarga los datos antes de pintar la ruta; si ya están en caché, no hace petición.
  loader: ({ context, deps }) => context.queryClient.ensureQueryData(listOptions(deps.filter)),
  component: TodosPage,
});

function TodosPage() {
  const { filter } = Route.useSearch();
  const { data: todos = [], isFetching } = useQuery(listOptions(filter));

  return (
    <Card>
      <CardHeader className="flex flex-col gap-4">
        <NewTodoForm />
        <nav className="flex items-center gap-1">
          {filterLinks.map((link) => (
            // Son navegación, así que son <Link> (enlaces reales) con estilo de botón.
            <Link
              key={link.label}
              to="/"
              search={{ filter: link.filter }}
              className={buttonVariants({ variant: "ghost", size: "sm" })}
              activeOptions={{ includeSearch: true }}
              activeProps={{ className: "bg-muted" }}
            >
              {link.label}
            </Link>
          ))}
          {isFetching && <Spinner className="ml-auto text-muted-foreground" />}
        </nav>
      </CardHeader>
      <CardContent>
        {todos.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted-foreground">No hay nada por aquí.</p>
        ) : (
          <ul className="divide-y">
            {todos.map((todo) => (
              <TodoItem key={todo.id} todo={todo} />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
