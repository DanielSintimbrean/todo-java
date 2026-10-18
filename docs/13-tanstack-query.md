# 13. TanStack Query y actualización optimista

Hasta ahora coordinabas peticiones con `useEffect`, estados de carga y un contador de recarga. TanStack Query guardará los datos remotos en una caché y coordinará la actualización de las listas.

## 1. Proporciona una caché a la aplicación

La dependencia se instaló al generar los helpers. Reemplaza `frontend/src/main.tsx` por:

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./api/client";
import "./index.css";

const queryClient = new QueryClient();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
```

Crea `QueryClient` fuera del componente para no reconstruir la caché al renderizar. `QueryClientProvider` permite que los hooks de los componentes accedan a esa misma instancia.

## 2. Sustituye los wrappers por hooks de mutación

Reemplaza `frontend/src/api/todos.ts` por su versión final:

### `frontend/src/api/todos.ts`

```ts
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { Todo } from "./client";
import {
  createTodoMutation,
  deleteTodoMutation,
  listTodosQueryKey,
  toggleTodoMutation,
  updateTodoMutation,
} from "./generated/@tanstack/react-query.gen";

// Sin argumentos, la key de `listTodos` es prefijo de todas sus variantes de filtro:
// TanStack Query compara las keys por coincidencia parcial.
const allTodosQueryKey = listTodosQueryKey();

function useInvalidateTodos() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: allTodosQueryKey });
}

export function useCreateTodo() {
  const invalidate = useInvalidateTodos();
  return useMutation({ ...createTodoMutation(), onSettled: invalidate });
}

export function useUpdateTodo() {
  const invalidate = useInvalidateTodos();
  return useMutation({ ...updateTodoMutation(), onSettled: invalidate });
}

export function useDeleteTodo() {
  const invalidate = useInvalidateTodos();
  return useMutation({ ...deleteTodoMutation(), onSettled: invalidate });
}

/**
 * Toggle con update optimista: el checkbox cambia al instante y, si el servidor falla,
 * se restauran las listas que había en caché. Al terminar se refetchea para quedar sincronizados
 * (y para que el TODO salga de la lista si estamos filtrando por activos/completados).
 */
export function useToggleTodo() {
  const queryClient = useQueryClient();
  return useMutation({
    ...toggleTodoMutation(),
    onMutate: async ({ path }) => {
      await queryClient.cancelQueries({ queryKey: allTodosQueryKey });
      const previous = queryClient.getQueriesData<Todo[]>({ queryKey: allTodosQueryKey });
      queryClient.setQueriesData<Todo[]>({ queryKey: allTodosQueryKey }, (todos) =>
        todos?.map((todo) =>
          todo.id === path.id ? { ...todo, completed: !todo.completed } : todo,
        ),
      );
      return { previous };
    },
    onError: (_error, _variables, context) => {
      for (const [queryKey, data] of context?.previous ?? []) {
        queryClient.setQueryData(queryKey, data);
      }
    },
    onSettled: () => queryClient.invalidateQueries({ queryKey: allTodosQueryKey }),
  });
}
```

Empieza leyendo `useCreateTodo`. `createTodoMutation()` aporta la función de petición y sus tipos. `useMutation` añade estados como `isPending`, `isError` y el método `mutate`. `onSettled` invalida las listas tanto después de un éxito como después de un error.

Una query key identifica qué datos se guardan. La lista de activos y la de completados tienen claves distintas. `listTodosQueryKey()` sin filtro sirve como coincidencia parcial para invalidar todas esas variantes.

Invalidar marca las entradas como desactualizadas y permite recargar las consultas activas. No significa que se borren todas las listas y se haga inmediatamente una petición por cada entrada inactiva.

## 3. Extrae un formulario pequeño

Crea `frontend/src/components/new-todo-form.tsx`:

```tsx
import { type FormEvent, useState } from "react";
import { useCreateTodo } from "../api/todos";
import { toProblem } from "../api/problem";

export function NewTodoForm() {
  const [title, setTitle] = useState("");
  const createTodo = useCreateTodo();

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    createTodo.mutate({ body: { title } }, { onSuccess: () => setTitle("") });
  }

  return (
    <form onSubmit={submit}>
      <input aria-label="Nuevo TODO" value={title}
        onChange={(event) => setTitle(event.target.value)} />
      <button disabled={createTodo.isPending}>Añadir</button>
      {createTodo.error && <p role="alert">{toProblem(createTodo.error).detail}</p>}
    </form>
  );
}
```

El título sigue en `useState` porque es un borrador local. El resultado del servidor y el estado de guardado pertenecen a la mutación.

## 4. Extrae una fila editable

Crea `frontend/src/components/todo-item.tsx`:

```tsx
import { type FormEvent, useState } from "react";
import type { Todo } from "../api/client";
import { toProblem } from "../api/problem";
import { useDeleteTodo, useToggleTodo, useUpdateTodo } from "../api/todos";

export function TodoItem({ todo }: { todo: Todo }) {
  const toggle = useToggleTodo();
  const update = useUpdateTodo();
  const remove = useDeleteTodo();
  const [draft, setDraft] = useState<string | null>(null);
  const error = toggle.error ?? update.error ?? remove.error;

  function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draft === null) return;
    update.mutate(
      { path: { id: todo.id }, body: { title: draft, version: todo.version } },
      { onSuccess: () => setDraft(null) },
    );
  }

  return (
    <li>
      <input type="checkbox" checked={todo.completed}
        aria-label={`Completar ${todo.title}`}
        onChange={() => toggle.mutate({ path: { id: todo.id } })} />
      {draft === null ? <span>{todo.title}</span> : (
        <form onSubmit={save}>
          <input aria-label="Título" value={draft}
            onChange={(event) => setDraft(event.target.value)} />
          <button disabled={update.isPending}>Guardar</button>
          <button type="button" onClick={() => setDraft(null)}>Cancelar</button>
        </form>
      )}
      <button onClick={() => setDraft(todo.title)}>Editar</button>
      <button disabled={remove.isPending}
        onClick={() => remove.mutate({ path: { id: todo.id } })}>Borrar</button>
      {error && <p role="alert">{toProblem(error).detail ?? toProblem(error).title}</p>}
    </li>
  );
}
```

## 5. Usa una query para la lista

Reemplaza `frontend/src/App.tsx`:

```tsx
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import type { TodoStatus } from "./api/client";
import { listTodosOptions } from "./api/generated/@tanstack/react-query.gen";
import { toProblem } from "./api/problem";
import { NewTodoForm } from "./components/new-todo-form";
import { TodoItem } from "./components/todo-item";

export default function App() {
  const [status, setStatus] = useState<TodoStatus>();
  const query = useQuery(listTodosOptions({ query: { status } }));

  return (
    <main>
      <h1>TODOs</h1>
      <NewTodoForm />
      <nav>
        <button onClick={() => setStatus(undefined)}>Todos</button>
        <button onClick={() => setStatus("ACTIVE")}>Activos</button>
        <button onClick={() => setStatus("COMPLETED")}>Completados</button>
      </nav>
      {query.isPending && <p>Cargando...</p>}
      {query.isFetching && !query.isPending && <p>Actualizando...</p>}
      {query.error && <p role="alert">{toProblem(query.error).title}</p>}
      {query.data?.length === 0 && <p>No hay tareas.</p>}
      <ul>{query.data?.map((todo) => <TodoItem key={todo.id} todo={todo} />)}</ul>
    </main>
  );
}
```

`isPending` indica que aún no hay datos resueltos de esa consulta. `isFetching` indica una petición en marcha, incluso cuando ya se muestran datos guardados.

## 6. Sigue el toggle optimista

Vuelve a `useToggleTodo` y léelo en orden:

1. `onMutate` cancela consultas de listas pendientes para evitar que sobrescriban la predicción inmediatamente.
2. Guarda las listas previas para poder restaurarlas.
3. Cambia el booleano de la tarea en cada lista guardada. El checkbox responde antes de terminar la petición.
4. `onError` restaura las listas si falla.
5. `onSettled` invalida y sincroniza con el servidor.

Esta predicción no recalcula el filtro: una tarea puede permanecer un instante en activos hasta que llegue la lista correcta. Tampoco actualiza anticipadamente `version` ni `updatedAt`. Es una actualización visual provisional.

El snapshot de varias listas simplifica el ejemplo. No garantiza que mutaciones simultáneas sobre la misma tarea se reconcilien de todas las formas posibles. La prueba de fallo del siguiente apartado debe hacerse con una operación cada vez.

## Comprobación

Crea una tarea y observa cómo la lista se actualiza sin un contador `revision`. Activa una red lenta en DevTools y marca una tarea: el checkbox cambia inmediatamente. Detén el backend, cambia una casilla y espera al fallo: debe restaurarse y aparecer el error.

El formulario conserva el título si el backend devuelve 400. Un conflicto de edición conserva el borrador y la invalidación permite recuperar la versión actual del servidor.

[Anterior](12-openapi-y-cliente.md) · [Índice](README.md) · [Siguiente: router y URL](14-tanstack-router.md)
