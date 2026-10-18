import createFetchClient, { type Middleware } from "openapi-fetch";
import createClient from "openapi-react-query";
import type { components, paths } from "./schema";

export type Todo = components["schemas"]["TodoResponse"];
export type Problem = components["schemas"]["ProblemDetail"];
export type TodoStatus = NonNullable<paths["/api/todos"]["get"]["parameters"]["query"]>["status"];

/**
 * El backend siempre responde los errores como ProblemDetail, pero lo que hay por medio no
 * (p. ej. un 502 text/plain vacío del proxy de Vite si el backend está parado). Esas respuestas
 * se convierten aquí en un ProblemDetail para que toda la app trate un único tipo de error.
 */
export const problemMiddleware: Middleware = {
  async onResponse({ response }) {
    if (response.ok || response.headers.get("content-type")?.includes("json")) {
      return undefined;
    }
    const text = await response.text();
    const problem: Problem = {
      type: "about:blank",
      title: response.statusText || "Error",
      status: response.status,
      detail: text || undefined,
    };
    return Response.json(problem, {
      status: response.status,
      headers: { "content-type": "application/problem+json" },
    });
  },
};

// Las rutas ya empiezan por /api, que el dev server de Vite redirige al backend.
export const fetchClient = createFetchClient<paths>();
fetchClient.use(problemMiddleware);

/**
 * Hooks de TanStack Query tipados a partir de openapi.json:
 * `$api.useQuery("get", "/api/todos", ...)`, `$api.useMutation("post", "/api/todos")`, `$api.queryOptions(...)`.
 */
export const $api = createClient(fetchClient);

/** Prefijo de la query key de `GET /api/todos`, para invalidar o editar todas sus variantes de filtro. */
export const todosQueryKey = ["get", "/api/todos"] as const;
