# 06. Listar y crear tareas desde React con fetch

El objetivo es usar la API que acabas de probar. Mantendremos las peticiones explícitas con `fetch` y los estados de React. Así verás qué trabajo automatizará TanStack Query más adelante.

## 1. Separa las peticiones del componente

Crea `frontend/src/api/todos.ts`:

```ts
export type Todo = {
  id: string;
  title: string;
  completed: boolean;
};

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new Error(`Error HTTP ${response.status}`);
  }
  return response.json() as Promise<T>;
}

export function listTodos(): Promise<Todo[]> {
  return requestJson<Todo[]>("/api/todos");
}

export function createTodo(title: string): Promise<Todo> {
  return requestJson<Todo>("/api/todos", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title }),
  });
}
```

`Todo` describe lo que esperamos recibir. `requestJson<T>` permite reutilizar la lectura de una respuesta para distintos tipos. La afirmación `as Promise<T>` no valida el JSON en ejecución. Por ahora debes mantener manualmente el acuerdo entre Java y TypeScript.

El GET no necesita cuerpo. En el POST convertimos el objeto a texto JSON y declaramos su formato con `Content-Type`.

## 2. Monta una página funcional

Reemplaza `frontend/src/App.tsx`:

```tsx
import { type FormEvent, useEffect, useState } from "react";
import { createTodo, listTodos, type Todo } from "./api/todos";

export default function App() {
  const [todos, setTodos] = useState<Todo[]>([]);
  const [title, setTitle] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    listTodos()
      .then((todos) => { if (active) setTodos(todos); })
      .catch((error: unknown) => {
        if (active) setError(error instanceof Error ? error.message : "Error inesperado");
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await createTodo(title);
      setTodos(await listTodos());
      setTitle("");
    } catch (error) {
      setError(error instanceof Error ? error.message : "Error inesperado");
    } finally {
      setSaving(false);
    }
  }

  return (
    <main>
      <h1>TODOs</h1>
      <form onSubmit={submit}>
        <input
          aria-label="Nuevo TODO"
          value={title}
          onChange={(event) => setTitle(event.target.value)}
        />
        <button disabled={loading || saving} type="submit">
          {saving ? "Guardando..." : "Añadir"}
        </button>
      </form>
      {error && <p role="alert">{error}</p>}
      {loading ? <p>Cargando...</p> : (
        todos.length === 0 ? <p>No hay tareas.</p> : (
          <ul>{todos.map((todo) => <li key={todo.id}>{todo.title}</li>)}</ul>
        )
      )}
    </main>
  );
}
```

`useEffect` carga la lista al montar el componente. La función de limpieza marca esa carga como inactiva para que su resultado no actualice un componente que ya se desmontó. No cancela la petición HTTP. En desarrollo, `StrictMode` puede ejecutar un ciclo adicional de montaje y limpieza; puedes observar dos GET iniciales.

El formulario llama a `preventDefault()` para evitar una navegación HTML tradicional. Después del POST hacemos un GET para volver a leer la verdad del servidor. La lista no se inventa el identificador.

El campo es controlado por React: su valor viene de `title` y cada cambio actualiza ese estado. La clave `key={todo.id}` permite identificar cada elemento entre renderizados.

## 3. Comprueba en el navegador

Abre la página y crea dos tareas. En Network debes encontrar un POST y un GET por cada envío. Recarga el navegador: las tareas siguen ahí mientras Java siga abierto. Reinicia Java: desaparecen.

Detén el backend y recarga. Debe aparecer el estado de error. Vuelve a arrancarlo y recarga para recuperarte.

## Comprobación

Puedes crear tareas desde la página y leerlas de nuevo. Aún hay duplicación manual de tipos y gestión de carga, errores y recarga. Ya tienes ejemplos concretos de lo que resolverán las herramientas posteriores.

[Anterior](05-todos-en-memoria.md) · [Índice](README.md) · [Siguiente: validación y errores](07-validacion-y-errores.md)
