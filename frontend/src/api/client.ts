import type { ListTodosData, ProblemDetail, TodoResponse } from "./generated";
import { client } from "./generated/client.gen";
import { toProblem } from "./problem";

export type Todo = TodoResponse;
export type Problem = ProblemDetail;
export type TodoStatus = NonNullable<ListTodosData["query"]>["status"];

/**
 * El backend siempre responde los errores como ProblemDetail, pero lo que hay por medio no
 * (p. ej. un 502 text/plain vacío del proxy de Vite si el backend está parado, o un fallo de red).
 * Este interceptor convierte cualquier error en un ProblemDetail para que toda la app trate un único tipo.
 * Se registra al importar este módulo (main.tsx lo importa al arrancar).
 */
client.interceptors.error.use((error, response) => toProblem(error, response));
