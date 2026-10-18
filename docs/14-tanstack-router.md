# 14. TanStack Router y filtros en la URL

Ahora los filtros viven en un estado local que se pierde al recargar. Vamos a representarlos como `?filter=active` y `?filter=completed`, para poder compartir enlaces y usar atrás y adelante.

## 1. Instala el router

Desde `frontend/`:

```sh
mise exec -- vp add @tanstack/react-router@1.170.45
mise exec -- vp add -D @tanstack/router-plugin@1.168.47
```

Reemplaza `frontend/vite.config.ts` por esta versión intermedia:

```ts
import path from "node:path";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, lazyPlugins } from "vite-plus";

export default defineConfig({
  plugins: lazyPlugins(() => [
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
  ]),
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "./src") },
  },
  server: { proxy: { "/api": "http://localhost:8080" } },
});
```

El plugin del router debe ir antes de React. Generará el árbol de rutas al arrancar o construir el frontend. No escribas `routeTree.gen.ts` a mano.

En `frontend/tsconfig.app.json`, añade dentro de `compilerOptions`:

```json
"paths": { "@/*": ["./src/*"] }
```

El alias de Vite resuelve imports durante la ejecución y construcción. `paths` permite a TypeScript entenderlos también. Si tu plantilla ya define estas opciones, actualízalas sin duplicar las claves JSON.

## 2. Crea la ruta raíz

Crea `frontend/src/routes/__root.tsx`:

### `frontend/src/routes/__root.tsx`

```tsx
import type { QueryClient } from "@tanstack/react-query";
import { createRootRouteWithContext, Outlet } from "@tanstack/react-router";

// Lo que reciben todos los loaders en `context` (se pasa en createRouter, en main.tsx).
type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
});

function RootLayout() {
  return (
    <main className="mx-auto flex min-h-svh max-w-xl flex-col gap-6 px-4 py-12">
      <h1 className="text-3xl font-semibold tracking-tight">TODOs</h1>
      <Outlet />
    </main>
  );
}
```

`Outlet` es donde se representa la ruta hija. El contexto incluye la misma instancia de `QueryClient` que usan los componentes. Las clases CSS tendrán su aspecto final cuando añadamos Tailwind.

## 3. Crea la página y valida el filtro

Crea `frontend/src/routes/index.tsx` con esta versión sin componentes de estilo:

```tsx
import { useQuery } from "@tanstack/react-query";
import { createFileRoute, Link } from "@tanstack/react-router";
import type { TodoStatus } from "@/api/client";
import { listTodosOptions } from "@/api/generated/@tanstack/react-query.gen";
import { NewTodoForm } from "@/components/new-todo-form";
import { TodoItem } from "@/components/todo-item";

type Filter = "active" | "completed";
type TodoSearch = { filter?: Filter };
const statusByFilter: Record<Filter, TodoStatus> = {
  active: "ACTIVE", completed: "COMPLETED",
};

function listOptions(filter: Filter | undefined) {
  return listTodosOptions({ query: { status: filter && statusByFilter[filter] } });
}

export const Route = createFileRoute("/")({
  validateSearch: (search: Record<string, unknown>): TodoSearch =>
    search.filter === "active" || search.filter === "completed" ? { filter: search.filter } : {},
  loaderDeps: ({ search }) => ({ filter: search.filter }),
  loader: ({ context, deps }) => context.queryClient.ensureQueryData(listOptions(deps.filter)),
  component: TodosPage,
});

function TodosPage() {
  const { filter } = Route.useSearch();
  const { data: todos = [], isFetching } = useQuery(listOptions(filter));
  return (
    <section>
      <NewTodoForm />
      <nav>
        <Link to="/" search={{ filter: undefined }}>Todos</Link>{" "}
        <Link to="/" search={{ filter: "active" }}>Activos</Link>{" "}
        <Link to="/" search={{ filter: "completed" }}>Completados</Link>
      </nav>
      {isFetching && <p>Actualizando...</p>}
      {todos.length === 0 && <p>No hay tareas.</p>}
      <ul>{todos.map((todo) => <TodoItem key={todo.id} todo={todo} />)}</ul>
    </section>
  );
}
```

`validateSearch` acepta solo los filtros conocidos. Una URL con otro valor se interpreta como ausencia de filtro; la función no promete reescribir automáticamente el texto de la barra de direcciones.

`loaderDeps` hace que la carga dependa del filtro. `ensureQueryData` obtiene los datos antes de resolver la ruta o reutiliza la entrada de caché si existe. El `useQuery` usa exactamente las mismas opciones y observa esa entrada. Puede hacer una recarga en segundo plano según su estado de frescura; no presupongas que nunca habrá otra petición.

## 4. Añade los estados de ruta

Crea `frontend/src/components/route-states.tsx`:

```tsx
import { type ErrorComponentProps, useRouter } from "@tanstack/react-router";
import { toProblem } from "../api/problem";

export function RoutePending() {
  return <p>Cargando ruta...</p>;
}

export function RouteError({ error }: ErrorComponentProps) {
  const router = useRouter();
  const problem = toProblem(error);
  return (
    <div>
      <p role="alert">{problem.detail ?? problem.title}</p>
      <button onClick={() => void router.invalidate()}>Reintentar</button>
    </div>
  );
}
```

## 5. Sustituye el arranque

Reemplaza `frontend/src/main.tsx` por la versión final:

### `frontend/src/main.tsx`

```tsx
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { createRouter, RouterProvider } from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { RouteError, RoutePending } from "./components/route-states";
import "./index.css";
// Configura el cliente generado del API (interceptor que normaliza errores a ProblemDetail).
import "./api/client";
import { routeTree } from "./routeTree.gen";

const queryClient = new QueryClient();

const router = createRouter({
  routeTree,
  context: { queryClient },
  defaultPreload: "intent",
  // La caché la gestiona TanStack Query, no el router.
  defaultPreloadStaleTime: 0,
  defaultPendingComponent: RoutePending,
  defaultErrorComponent: RouteError,
});

// Registra el router para que <Link>, useSearch, etc. estén tipados con nuestras rutas.
declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router;
  }
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
```

`QueryClientProvider` proporciona la caché a React y el contexto del router la proporciona a los loaders. Es la misma caché. `defaultPreloadStaleTime: 0` deja las decisiones de datos a Query en lugar de mantener una segunda política de frescura en el router.

La ampliación de módulo registra el tipo del router para que enlaces y parámetros tengan comprobación TypeScript.

Elimina `frontend/src/App.tsx`, que ya no se importa. Reinicia `vp dev` para generar `src/routeTree.gen.ts`. Si el editor indica que falta ese fichero antes del primer arranque, ejecuta el servidor o `vp build`.

## Comprobación

Abre `http://localhost:5173/?filter=active`, recarga y comprueba que sigues viendo activos. Cambia a completados y usa atrás. La URL y la lista deben moverse juntas.

Detén Java y entra directamente a una ruta sin datos en caché. Debe aparecer el error de ruta. Arranca Java y pulsa Reintentar.

[Anterior](13-tanstack-query.md) · [Índice](README.md) · [Siguiente: interfaz final](15-interfaz-y-tests-frontend.md)
