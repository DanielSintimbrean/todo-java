import type { Problem } from "./client";

/**
 * Normaliza un error en un ProblemDetail. Si ya lo es, se devuelve tal cual; si hay respuesta HTTP
 * se construye a partir de su estado; si no, es que la petición ni siquiera llegó a hacerse.
 */
export function toProblem(error: unknown, response?: Response): Problem {
  if (isProblem(error)) {
    return error;
  }
  if (response) {
    return {
      type: "about:blank",
      title: response.statusText || "Error",
      status: response.status,
      detail: typeof error === "string" && error !== "" ? error : undefined,
    };
  }
  return {
    type: "about:blank",
    title: error instanceof Error ? "Network error" : "Unexpected error",
    status: 0,
    detail: error instanceof Error ? error.message : undefined,
  };
}

function isProblem(error: unknown): error is Problem {
  return typeof error === "object" && error !== null && "title" in error && "status" in error;
}
