import type { QueryClient } from "@tanstack/react-query";
import { createRootRouteWithContext, Outlet, useRouter } from "@tanstack/react-router";
import { ProblemAlert } from "@/components/problem-alert";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";

// Lo que reciben todos los loaders en `context` (se pasa en createRouter, en main.tsx).
type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
  pendingComponent: () => (
    <div className="flex justify-center p-8">
      <Spinner className="size-6" />
    </div>
  ),
  errorComponent: RootError,
});

function RootLayout() {
  return (
    <main className="mx-auto flex min-h-svh max-w-xl flex-col gap-6 px-4 py-12">
      <h1 className="text-3xl font-semibold tracking-tight">TODOs</h1>
      <Outlet />
    </main>
  );
}

function RootError({ error }: { error: unknown }) {
  const router = useRouter();
  return (
    <div className="flex flex-col items-start gap-3">
      <ProblemAlert error={error} />
      <Button variant="outline" onClick={() => router.invalidate()}>
        Reintentar
      </Button>
    </div>
  );
}
