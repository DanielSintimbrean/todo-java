# 03. Hello world desde el frontend

Ahora escribirás una página React que muestra un mensaje propio. El backend puede seguir abierto, pero esta página todavía no lo utiliza.

## 1. Sustituye la plantilla

Crea o reemplaza `frontend/src/App.tsx`:

```tsx
export default function App() {
  return (
    <main>
      <h1>Aprendiendo Java</h1>
      <p>Hello world desde React</p>
    </main>
  );
}
```

Reemplaza `frontend/src/main.tsx`:

```tsx
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
```

Reemplaza `frontend/src/index.css`:

```css
body {
  font-family: sans-serif;
  margin: 0;
}

main {
  max-width: 640px;
  margin: 48px auto;
  padding: 0 16px;
}
```

Elimina los ficheros de demostración que ya no uses, por ejemplo `App.css` y los logos de la plantilla. Si el generador incluía un router o componentes de ejemplo, retira sus imports del arranque. La página de este capítulo solo necesita estos tres ficheros y un `index.html` con `<div id="root"></div>`.

## 2. Entiende qué se ejecuta

`App` es una función que devuelve JSX. JSX describe los elementos que React debe representar. `createRoot` localiza el elemento HTML con identificador `root` y monta la aplicación allí.

El `!` después de `getElementById` le dice a TypeScript que contamos con que ese elemento existe. No lo crea ni comprueba nada en tiempo de ejecución. Si cambias el identificador en `index.html`, debes cambiarlo aquí también.

TypeScript comprueba tipos durante el desarrollo. Vite transforma el código para que el navegador lo pueda ejecutar. El backend Java no interpreta estos ficheros.

## 3. Arranca y modifica

En una segunda terminal, desde la raíz:

```sh
cd frontend
mise exec -- vp dev --port 5173 --strictPort
```

Abre `http://localhost:5173`. Debes ver los dos textos. Cambia el mensaje de React y guarda. Vite actualizará la página sin que reinicies Java.

`--strictPort` hace que el arranque falle si 5173 está ocupado, en lugar de elegir otro puerto sin que lo adviertas.

## Comprobación

La página muestra el mensaje aunque el backend esté detenido. Si abres las herramientas del navegador y miras Network, no hay ninguna petición a `/api/hello`.

Tenemos dos comprobaciones separadas: Java responde en `:8080` y React muestra una página en `:5173`. El siguiente paso los conecta.

[Anterior](02-hello-backend.md) · [Índice](README.md) · [Siguiente: primera request con fetch](04-primera-request.md)
