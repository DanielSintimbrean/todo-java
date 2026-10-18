import type { Problem } from "./client";

/**
 * openapi-fetch devuelve como error el body ya parseado: normalmente un ProblemDetail del backend,
 * pero puede ser texto (p. ej. un 502 del proxy si el backend no está arrancado) o un Error de red.
 */
export function toProblem(error: unknown): Problem {
  if (isProblem(error)) {
    return error;
  }
  if (error instanceof Error) {
    return { type: "about:blank", title: "Network error", status: 0, detail: error.message };
  }
  return {
    type: "about:blank",
    title: "Unexpected error",
    status: 0,
    detail: typeof error === "string" && error !== "" ? error : undefined,
  };
}

function isProblem(error: unknown): error is Problem {
  return typeof error === "object" && error !== null && "title" in error && "status" in error;
}
