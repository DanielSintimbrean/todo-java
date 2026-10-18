import { type ErrorComponentProps, useRouter } from "@tanstack/react-router";
import { ProblemAlert } from "@/components/problem-alert";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

/** Se muestra mientras el loader de una ruta tarda (ver defaultPendingMs en createRouter). */
export function RoutePending() {
  return (
    <div className="flex justify-center p-8">
      <Spinner className="size-6" />
    </div>
  );
}

/** Se muestra si falla el loader o el render de una ruta. */
export function RouteError({ error }: ErrorComponentProps) {
  const router = useRouter();
  return (
    <div className="flex flex-col items-start gap-3">
      <ProblemAlert error={error} />
      {/* Vuelve a ejecutar los loaders de la ruta actual. */}
      <Button variant="outline" onClick={() => router.invalidate()}>
        Reintentar
      </Button>
    </div>
  );
}
