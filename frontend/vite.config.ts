import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react from "@vitejs/plugin-react";
import { defineConfig, lazyPlugins } from "vite-plus";

// Ficheros generados: no se formatean ni se lintan a mano.
const generated = ["src/api/generated/**", "src/routeTree.gen.ts"];

// https://viteplus.dev/config/
export default defineConfig({
  // El plugin del router genera src/routeTree.gen.ts a partir de src/routes/ y tiene que ir antes que react().
  plugins: lazyPlugins(() => [
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
    tailwindcss(),
  ]),
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "./src") },
  },
  server: {
    // Mismo origen que el frontend: el navegador no necesita CORS.
    proxy: { "/api": "http://localhost:8080" },
  },
  test: {
    environment: "jsdom",
  },
  fmt: {
    ignorePatterns: generated,
  },
  lint: {
    ignorePatterns: generated,
    plugins: ["react", "typescript", "oxc"],
    rules: {
      "react/rules-of-hooks": "error",
      "react/only-export-components": [
        "warn",
        {
          allowConstantExport: true,
        },
      ],
      "vite-plus/prefer-vite-plus-imports": "error",
    },
    overrides: [
      {
        // - Rutas: exportan `Route` junto a sus componentes; el plugin del router se encarga del fast refresh.
        // - Componentes de shadcn: exportan también sus variantes (buttonVariants...).
        files: ["src/routes/**", "src/components/ui/**"],
        rules: { "react/only-export-components": "off" },
      },
    ],
    options: {
      typeAware: true,
      typeCheck: true,
    },
    jsPlugins: [
      {
        name: "vite-plus",
        specifier: "vite-plus/oxlint-plugin",
      },
    ],
  },
});
