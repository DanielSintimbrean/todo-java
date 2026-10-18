import { defineConfig } from "@hey-api/openapi-ts";

// Genera src/api/generated/ a partir del contrato del backend: tipos, cliente fetch y
// helpers de TanStack Query (queryOptions, queryKey y mutationOptions por operación).
export default defineConfig({
  input: "../backend/openapi.json",
  output: "src/api/generated",
  plugins: [
    // Sin baseUrl: las rutas ya empiezan por /api, que el dev server de Vite redirige al backend.
    // (Con el `servers: [{ url: "/" }]` del contrato saldría "//api/todos", una URL de otro host.)
    { name: "@hey-api/client-fetch", baseUrl: false },
    "@tanstack/react-query",
  ],
});
