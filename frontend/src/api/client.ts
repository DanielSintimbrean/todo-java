import createFetchClient from "openapi-fetch";
import createClient from "openapi-react-query";
import type { components, paths } from "./schema";

export type Todo = components["schemas"]["TodoResponse"];
export type Problem = components["schemas"]["ProblemDetail"];
export type TodoStatus = NonNullable<paths["/api/todos"]["get"]["parameters"]["query"]>["status"];

// Las rutas ya empiezan por /api, que el dev server de Vite redirige al backend.
export const fetchClient = createFetchClient<paths>();

/**
 * Hooks de TanStack Query tipados a partir de openapi.json:
 * `$api.useQuery("get", "/api/todos", ...)`, `$api.useMutation("post", "/api/todos")`, `$api.queryOptions(...)`.
 */
export const $api = createClient(fetchClient);

/** Prefijo de la query key de `GET /api/todos`, para invalidar o editar todas sus variantes de filtro. */
export const todosQueryKey = ["get", "/api/todos"] as const;
