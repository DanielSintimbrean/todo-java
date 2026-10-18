import { CircleAlertIcon } from "lucide-react";
import { toProblem } from "@/api/problem";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

/** Muestra un error del API (ProblemDetail, RFC 9457) con el detalle y los errores por campo. */
export function ProblemAlert({ error }: { error: unknown }) {
  const problem = toProblem(error);
  const fieldErrors = Object.entries(problem.errors ?? {});

  return (
    <Alert variant="destructive">
      <CircleAlertIcon />
      <AlertTitle>
        {problem.title}
        {problem.status > 0 && ` (${problem.status})`}
      </AlertTitle>
      {(problem.detail || fieldErrors.length > 0) && (
        <AlertDescription>
          {problem.detail && <p>{problem.detail}</p>}
          {fieldErrors.length > 0 && (
            <ul className="list-disc pl-4">
              {fieldErrors.map(([field, message]) => (
                <li key={field}>
                  <span className="font-medium">{field}</span>: {message}
                </li>
              ))}
            </ul>
          )}
        </AlertDescription>
      )}
    </Alert>
  );
}
