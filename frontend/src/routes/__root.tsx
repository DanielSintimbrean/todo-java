import type { QueryClient } from "@tanstack/react-query";
import { createRootRouteWithContext, Outlet } from "@tanstack/react-router";

// Lo que reciben todos los loaders en `context` (se pasa en createRouter, en main.tsx).
type RouterContext = {
  queryClient: QueryClient;
};

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
});

function RootLayout() {
  return (
    <main className="mx-auto flex min-h-svh max-w-xl flex-col gap-6 px-4 py-12">
      <h1 className="text-3xl font-semibold tracking-tight">TODOs</h1>
      <Outlet />
    </main>
  );
}
