# 04. Primera request del frontend al backend con fetch

El navegador pedirá el mensaje a Java y lo mostrará. No instales ninguna dependencia para HTTP. `fetch` ya está disponible en el navegador.

## 1. Configura el proxy de desarrollo

Reemplaza `frontend/vite.config.ts` por esta configuración mínima:

```ts
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite-plus";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
```

Reinicia el servidor del frontend después de cambiar su configuración.

La página está en `http://localhost:5173`. Si llama a `/api/hello`, el navegador pide `http://localhost:5173/api/hello`. Vite reconoce el prefijo `/api` y reenvía la petición a `http://localhost:8080/api/hello`.

El navegador habla con su mismo origen. Los puertos 5173 y 8080 serían orígenes distintos si llamaras directamente a 8080. Con este proxy no necesitamos configurar CORS en Java.

Este proxy es del servidor de desarrollo. Al desplegar habrá que servir `/api` mediante un proxy o una configuración equivalente. `vp build` no incorpora un servidor proxy al JavaScript generado.

## 2. Haz la petición al pulsar un botón

Reemplaza `frontend/src/App.tsx`:

```tsx
import { useState } from "react";

export default function App() {
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadMessage() {
    setLoading(true);
    setError(null);
    setMessage("");

    try {
      const response = await fetch("/api/hello");
      if (!response.ok) {
        throw new Error(`El servidor respondió ${response.status}`);
      }
      const text = await response.text();
      setMessage(text);
    } catch (error) {
      setError(error instanceof Error ? error.message : "Error inesperado");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main>
      <h1>Aprendiendo Java</h1>
      <p>Hello world desde React</p>
      <button onClick={loadMessage} disabled={loading}>
        {loading ? "Cargando..." : "Pedir saludo a Java"}
      </button>
      {message && <p>{message}</p>}
      {error && <p role="alert">{error}</p>}
    </main>
  );
}
```

`useState` conserva datos entre renderizados y sus funciones `set...` provocan que React actualice la pantalla. `async` permite usar `await` para esperar una promesa sin bloquear el navegador.

El primer `await` espera la respuesta HTTP. El segundo lee su cuerpo como texto. Usamos `text()` porque el controlador devuelve un `String`. Cuando devolvamos un objeto JSON, usaremos `json()`.

`fetch` no lanza una excepción automáticamente por un 404 o un 500. Por eso comprobamos `response.ok`, que cubre los estados 200 a 299. `catch` también recoge fallos de red y `finally` quita siempre el estado de carga.

## 3. Comprueba los dos trayectos

Con ambos servidores abiertos, prueba desde otra terminal:

```sh
curl -i http://localhost:8080/api/hello
curl -i http://localhost:5173/api/hello
```

Ambos deben devolver el mismo texto. La segunda petición pasa por Vite. Después pulsa el botón en la página y localiza `GET /api/hello` en Network.

## 4. Provoca un fallo

Detén Java y pulsa otra vez. La página debe mostrar un error y el botón debe volver a estar disponible. Según la respuesta del proxy, puede aparecer un 500 o un fallo de conexión. Arranca Java de nuevo y vuelve a pulsar.

No lo arregles añadiendo `mode: "no-cors"`. Ese modo no permite leer el cuerpo de una respuesta de otro origen. Revisa primero que la URL sea relativa y que el proxy esté cargado.

## Comprobación

La página muestra el texto devuelto por Java y refleja el fallo cuando Java no está disponible. No has añadido Axios, TanStack Query ni un cliente generado. Ya puedes seguir el recorrido completo de una petición antes de incorporar herramientas que lo automaticen.

[Anterior](03-hello-frontend.md) · [Índice](README.md) · [Siguiente: tareas en memoria](05-todos-en-memoria.md)
