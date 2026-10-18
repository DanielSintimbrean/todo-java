# 15. Interfaz final y tests de frontend

Las operaciones y la navegación ya funcionan. Ahora cambiaremos los controles sencillos por los componentes de la aplicación final y añadiremos los tests de errores. El backend no necesita cambios en este capítulo.

## 1. Instala los componentes y estilos

Desde `frontend/` instala las versiones del proyecto:

```sh
mise exec -- vp add @base-ui/react@1.9.0 @fontsource-variable/geist@5.3.0 @tailwindcss/vite@4.3.3 tailwindcss@4.3.3 tw-animate-css@1.4.0 shadcn@4.22.0 lucide-react@1.59.0 class-variance-authority@0.7.1 cn@0.5.0
mise exec -- vp add -D @testing-library/dom@10.4.2 @testing-library/react@16.3.3 jsdom@30.1.3
```

Tailwind transforma utilidades CSS. Base UI aporta el comportamiento de controles como el checkbox. Los componentes de shadcn son código que vive en nuestro repositorio, no una pantalla remota que se descarga al usarla.

Crea `frontend/components.json`:

### `frontend/components.json`

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "base-nova",
  "rsc": false,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/index.css",
    "baseColor": "neutral",
    "cssVariables": true,
    "prefix": ""
  },
  "iconLibrary": "lucide",
  "rtl": false,
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "menuColor": "default",
  "menuAccent": "subtle",
  "registries": {}
}
```

Puedes generar los componentes con `mise exec -- vp exec shadcn add button input checkbox card alert spinner`. El registro remoto puede cambiar; para reproducir exactamente este checkout, usa los contenidos completos de [componentes UI y estilos](15b-componentes-ui.md). Ese anexo incluye también `src/lib/utils.ts`, `src/index.css` y el favicon. Incorpóralos antes de sustituir los componentes de la página.

En `vite.config.ts`, importa `tailwindcss` de `@tailwindcss/vite` y añade `tailwindcss()` después de `react()` en el array de plugins. Añade también `test: { environment: "jsdom" }` en la configuración. En el capítulo 16 tendrás el fichero final completo con lint y formato.

## 2. Muestra los errores completos

Crea `ProblemAlert` y reemplaza los estados de ruta:

### `frontend/src/components/problem-alert.tsx`

```tsx
import { CircleAlertIcon } from "lucide-react";
import { toProblem } from "@/api/problem";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

/** Muestra un error del API (ProblemDetail, RFC 9457) con el detalle y los errores por campo. */
export function ProblemAlert({ error }: { error: unknown }) {
  const problem = toProblem(error);
  const fieldErrors = Object.entries(problem.errors ?? {});

  return (
    <Alert variant="destructive">
      <CircleAlertIcon />
      <AlertTitle>
        {problem.title}
        {problem.status > 0 && ` (${problem.status})`}
      </AlertTitle>
      {(problem.detail || fieldErrors.length > 0) && (
        <AlertDescription>
          {problem.detail && <p>{problem.detail}</p>}
          {fieldErrors.length > 0 && (
            <ul className="list-disc pl-4">
              {fieldErrors.map(([field, message]) => (
                <li key={field}>
                  <span className="font-medium">{field}</span>: {message}
                </li>
              ))}
            </ul>
          )}
        </AlertDescription>
      )}
    </Alert>
  );
}
```

### `frontend/src/components/route-states.tsx`

```tsx
import { type ErrorComponentProps, useRouter } from "@tanstack/react-router";
import { ProblemAlert } from "@/components/problem-alert";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

/** Se muestra mientras el loader de una ruta tarda (ver defaultPendingMs en createRouter). */
export function RoutePending() {
  return (
    <div className="flex justify-center p-8">
      <Spinner className="size-6" />
    </div>
  );
}

/** Se muestra si falla el loader o el render de una ruta. */
export function RouteError({ error }: ErrorComponentProps) {
  const router = useRouter();
  return (
    <div className="flex flex-col items-start gap-3">
      <ProblemAlert error={error} />
      {/* Vuelve a ejecutar los loaders de la ruta actual. */}
      <Button variant="outline" onClick={() => router.invalidate()}>
        Reintentar
      </Button>
    </div>
  );
}
```

`ProblemAlert` recibe un valor desconocido y lo normaliza. Muestra el título, el estado cuando existe, el detalle y los errores por campo. Deja de depender de que todas las excepciones sean instancias de `Error`.

## 3. Sustituye formulario y fila

### `frontend/src/components/new-todo-form.tsx`

```tsx
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
```

### `frontend/src/components/todo-item.tsx`

```tsx
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
      { path, body: { title: draft, version: todo.version } },
      { onSuccess: () => setDraft(null) },
    );
  }

  return (
    <li className="flex flex-col gap-2 py-2">
      <div className="flex items-center gap-3">
        <Checkbox
          checked={todo.completed}
          onCheckedChange={() => toggleTodo.mutate({ path })}
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
          onClick={() => deleteTodo.mutate({ path })}
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
```

Lee primero el formulario. El título se vacía solo en `onSuccess`. El botón se deshabilita mientras se envía y un error aparece debajo. Se mantiene la validación del servidor visible.

En la fila, `draft === null` significa que no se está editando. Al pulsar Editar o hacer doble clic se copia el título al borrador. Enter envía el formulario, perder el foco intenta guardar y Escape cancela. Si el título no ha cambiado, la edición se cierra sin PATCH.

La petición de edición incluye `todo.version`. Cuando la mutación termina, los hooks del capítulo 13 invalidan las listas. No copies esa invalidación en cada botón.

## 4. Sustituye la página

### `frontend/src/routes/index.tsx`

```tsx
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
```

Los filtros siguen siendo enlaces porque navegan entre URLs. `buttonVariants` solo les da apariencia de botón. El spinner indica una consulta en curso aunque ya haya una lista visible.

El componente de página orquesta formulario, filtros y filas. Los hooks de API conservan la coordinación de peticiones y caché. Esta separación permite cambiar el aspecto sin reescribir la lógica HTTP.

## 5. Añade los tests del frontend

Crea los dos ficheros siguientes:

### `frontend/src/api/problem.test.ts`

```ts
import { describe, expect, it } from "vite-plus/test";
import "./client";
import { listTodos } from "./generated";
import { toProblem } from "./problem";

describe("toProblem", () => {
  it("returns ProblemDetail bodies as they are", () => {
    const problem = { type: "about:blank", title: "Todo not found", status: 404, detail: "Nope" };

    expect(toProblem(problem)).toBe(problem);
  });

  it("wraps network errors", () => {
    expect(toProblem(new TypeError("Failed to fetch"))).toMatchObject({
      title: "Network error",
      detail: "Failed to fetch",
    });
  });
});

describe("API client", () => {
  it("turns non-JSON error responses into a ProblemDetail", async () => {
    const fetch = async () => new Response("", { status: 502, statusText: "Bad Gateway" });

    const { error } = await listTodos({ baseUrl: "http://localhost", fetch });

    expect(error).toEqual({ type: "about:blank", title: "Bad Gateway", status: 502 });
  });
});
```

### `frontend/src/components/problem-alert.test.tsx`

```tsx
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vite-plus/test";
import { ProblemAlert } from "./problem-alert";

afterEach(cleanup);

describe("ProblemAlert", () => {
  it("shows title, status, detail and field errors", () => {
    render(
      <ProblemAlert
        error={{
          type: "about:blank",
          title: "Bad Request",
          status: 400,
          detail: "Validation failed",
          errors: { title: "must not be blank" },
        }}
      />,
    );

    expect(screen.getByRole("alert").textContent).toContain("Bad Request (400)");
    expect(screen.getByText("Validation failed")).toBeTruthy();
    expect(screen.getByText("title").parentElement?.textContent).toBe("title: must not be blank");
  });
});
```

El test de cliente inyecta una función fetch que devuelve 502 sin JSON. Comprueba el comportamiento ante un proxy, sin depender de un servidor real. El test de componente comprueba el contenido visible del aviso, incluidos los errores de campo.

`cleanup` desmonta lo que se haya renderizado para que un test no contamine al siguiente. `jsdom` proporciona un DOM para estos tests; no sustituye a comprobar la aplicación en un navegador real.

Desde `frontend/`:

```sh
mise exec -- vp test
mise exec -- vp build
```

## Comprobación

Los cuatro casos de test pasan. La página tiene el formulario, las tres opciones de filtro y las acciones de cada tarea. Comprueba crear, editar con Enter, cancelar con Escape, guardar al perder el foco, alternar y borrar. Prueba un título vacío y un fallo de red.

La interfaz debe poder usarse con teclado y los controles de icono deben conservar sus etiquetas `aria-label`. Usa los enlaces de filtros para comprobar que se mantiene la navegación por URL.

[Anterior](14-tanstack-router.md) · [Índice](README.md) · [Siguiente: tooling y verificación](16-tooling-y-verificacion.md)
