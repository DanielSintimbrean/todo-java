import type { Problem } from "./client";

/**
 * Normaliza lo que llega como `error` a TanStack Query: un ProblemDetail del API
 * (ver problemMiddleware en client.ts) o un Error si la petición ni siquiera llegó a hacerse.
 */
export function toProblem(error: unknown): Problem {
  if (isProblem(error)) {
    return error;
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
