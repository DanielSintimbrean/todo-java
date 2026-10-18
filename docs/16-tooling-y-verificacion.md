# 16. Tooling final y comprobación de la reconstrucción

La aplicación ya funciona. Queda fijar las herramientas, reunir los comandos de desarrollo y comprobar que la reconstrucción coincide con el resultado del repositorio.

## 1. Revisa las versiones del frontend

El generador del primer capítulo pudo elegir versiones distintas. Usa este manifiesto final y su catálogo, conservando los ficheros de código que has construido:

### `frontend/package.json`

```json
{
  "name": "frontend",
  "version": "0.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vp dev",
    "build": "vp build",
    "preview": "vp preview",
    "api:generate": "openapi-ts"
  },
  "dependencies": {
    "@base-ui/react": "1.9.0",
    "@fontsource-variable/geist": "5.3.0",
    "@tailwindcss/vite": "4.3.3",
    "@tanstack/react-query": "5.104.3",
    "@tanstack/react-router": "1.170.45",
    "class-variance-authority": "0.7.1",
    "cn": "0.5.0",
    "lucide-react": "1.59.0",
    "react": "19.3.1",
    "react-dom": "19.3.1",
    "shadcn": "4.22.0",
    "tailwindcss": "4.3.3",
    "tw-animate-css": "1.4.0"
  },
  "devDependencies": {
    "@hey-api/openapi-ts": "0.0.0-next-20260930190945",
    "@tanstack/router-plugin": "1.168.47",
    "@testing-library/dom": "10.4.2",
    "@testing-library/react": "16.3.3",
    "@types/node": "24.19.2",
    "@types/react": "19.3.0",
    "@types/react-dom": "19.3.0",
    "@vitejs/plugin-react": "6.1.2",
    "jsdom": "30.1.3",
    "typescript": "7.0.2",
    "vite": "catalog:",
    "vite-plus": "catalog:"
  },
  "devEngines": {
    "packageManager": {
      "name": "pnpm",
      "version": "12.14.0",
      "onFail": "download"
    },
    "runtime": {
      "name": "node",
      "version": "24.22.0",
      "onFail": "download"
    }
  }
}
```

### `frontend/pnpm-workspace.yaml`

```yaml
catalog:
  vite: npm:@voidzero-dev/vite-plus-core@1.3.0
  vite-plus: 1.3.0
overrides:
  vite@*: "catalog:"
peerDependencyRules:
  allowAny:
    - vite
  allowedVersions:
    vite: "*"
# `vp add` guarda versiones exactas (sin ^ ni ~).
saveExact: true
```

`devEngines` fija las versiones de Node y pnpm que gestiona Vite+. `catalog` fija Vite+ y su versión de Vite. `saveExact` evita introducir rangos al añadir dependencias.

Desde `frontend/` ejecuta `mise exec -- vp install` para generar o actualizar `pnpm-lock.yaml`. Guarda el lockfile. Para reproducir además la resolución exacta de este checkout, puedes usar su [lockfile de referencia](../frontend/pnpm-lock.yaml) junto a estos mismos manifiestos y ejecutar `mise exec -- vp install --frozen-lockfile`.

## 2. Completa la configuración de TypeScript y Vite+

Compara estos tres ficheros con los de tu proyecto y déjalos con este contenido. El alias debe estar tanto en TypeScript como en Vite.

### `frontend/tsconfig.json`

```json
{
  "files": [],
  "references": [
    {
      "path": "./tsconfig.app.json"
    },
    {
      "path": "./tsconfig.node.json"
    }
  ],
  "compilerOptions": {
    "paths": {
      "@/*": ["./src/*"]
    }
  }
}
```

### `frontend/tsconfig.app.json`

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.app.tsbuildinfo",
    "target": "es2023",
    "lib": ["ES2023", "DOM"],
    "module": "esnext",
    "types": ["vite/client"],
    "allowArbitraryExtensions": true,
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "react-jsx",
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "erasableSyntaxOnly": true,
    "noFallthroughCasesInSwitch": true,
    "paths": {
      "@/*": ["./src/*"]
    }
  },
  "include": ["src"]
}
```

### `frontend/tsconfig.node.json`

```json
{
  "compilerOptions": {
    "tsBuildInfoFile": "./node_modules/.tmp/tsconfig.node.tsbuildinfo",
    "target": "es2023",
    "lib": ["ES2023"],
    "types": ["node"],
    "skipLibCheck": true,

    /* Bundler mode */
    "module": "nodenext",
    "allowImportingTsExtensions": true,
    "verbatimModuleSyntax": true,
    "moduleDetection": "force",
    "noEmit": true,

    /* Linting */
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "erasableSyntaxOnly": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["vite.config.ts"]
}
```

### `frontend/vite.config.ts`

```ts
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
```

La configuración final une router, React y Tailwind. Excluye los ficheros generados de formato y lint porque su dueño es el generador. Los tests usan jsdom y el lint comprueba hooks y tipos.

## 3. Añade el formato de Java

Hasta ahora el POM se ha ampliado poco a poco. Reemplázalo por la versión final para incorporar Spotless y palantir-java-format y comprobar que no falta ninguna dependencia:

### `backend/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>4.1.1</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.example</groupId>
	<artifactId>todo</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>todo</name>
	<description>TODO app</description>

	<properties>
		<java.version>25</java.version>
		<!-- springdoc 3.x es la línea para Spring Boot 4; 3.1.x está construida contra Boot 4.1 -->
		<springdoc.version>3.1.1</springdoc.version>
		<spotless.version>3.10.4</spotless.version>
		<palantir-java-format.version>2.102.0</palantir-java-format.version>
	</properties>

	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
			<version>${springdoc.version}</version>
		</dependency>
		<dependency>
			<groupId>org.jspecify</groupId>
			<artifactId>jspecify</artifactId>
		</dependency>

		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
		<!-- Levanta ../compose.yaml al arrancar la app en local (no se usa en tests) -->
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-docker-compose</artifactId>
			<scope>runtime</scope>
			<optional>true</optional>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-webmvc-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-flyway-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-testcontainers</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-junit-jupiter</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>org.testcontainers</groupId>
			<artifactId>testcontainers-postgresql</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
			<!--
				Formateo del código Java (el equivalente a `vp fmt` / `vp check` del frontend):
				  ./mvnw spotless:apply   formatea
				  ./mvnw spotless:check   falla si algo no está formateado (se ejecuta en `verify`)
			-->
			<plugin>
				<groupId>com.diffplug.spotless</groupId>
				<artifactId>spotless-maven-plugin</artifactId>
				<version>${spotless.version}</version>
				<configuration>
					<java>
						<palantirJavaFormat>
							<version>${palantir-java-format.version}</version>
						</palantirJavaFormat>
					</java>
				</configuration>
				<executions>
					<execution>
						<goals>
							<goal>check</goal>
						</goals>
					</execution>
				</executions>
			</plugin>
		</plugins>
	</build>

</project>
```

Desde `backend/`:

```sh
mise exec -- ./mvnw spotless:apply
mise exec -- ./mvnw verify
```

`spotless:apply` modifica el formato. `verify` comprueba formato y ejecuta los tests. Si solo has cambiado espacios, no necesitas modificar la lógica para que pase.

## 4. Activa virtual threads y conserva las propiedades finales

### `backend/src/main/resources/application.properties`

```properties
spring.application.name=todo

# Cada request se atiende en un virtual thread (Project Loom) en lugar de un hilo del pool de Tomcat.
spring.threads.virtual.enabled=true

# Flyway es el único dueño del esquema; Hibernate solo comprueba que las entidades encajan con las tablas.
spring.jpa.hibernate.ddl-auto=validate
# Sin open-in-view: la sesión de JPA vive lo que dura la transacción del service, no toda la request.
spring.jpa.open-in-view=false

# En local, Spring Boot levanta este compose y configura el DataSource con sus credenciales.
spring.docker.compose.file=../compose.yaml
spring.docker.compose.lifecycle-management=start-only

# Salida determinista para que backend/openapi.json no cambie entre ejecuciones.
springdoc.writer-with-default-pretty-printer=true
springdoc.writer-with-order-by-keys=true
springdoc.default-produces-media-type=application/json
```

`spring.threads.virtual.enabled=true` permite atender peticiones con virtual threads. El código de este proyecto sigue usando Spring MVC y llamadas bloqueantes a JPA. Un virtual thread facilita esperar I/O sin ocupar todo el tiempo un hilo del sistema operativo. No elimina el límite de conexiones de PostgreSQL ni hace más rápidos los cálculos intensivos de CPU.

En Java, `@Transactional` funciona mediante un proxy de Spring. Las llamadas que cruzan ese proxy reciben la gestión transaccional. Una llamada interna a otro método de la misma instancia no pasa automáticamente por el proxy. El controlador de este proyecto llama al servicio inyectado y sí cruza esa frontera.

## 5. Reúne las tareas de desarrollo

Reemplaza el `mise.toml` mínimo por el final:

### `mise.toml`

```toml
# Vite+ (`vp`) no está bien empaquetado en el registro de mise (`vp` = npm:vite-plus, sin la CLI nativa),
# así que se instala con este plugin de la comunidad, que replica el instalador oficial.
# Ver https://github.com/voidzero-dev/vite-plus/issues/943
[plugins]
vite-plus = "https://github.com/markmals/mise-vite-plus"

# mise gestiona Java y `vp`. Node y pnpm los gestiona `vp`, con las versiones de
# `devEngines` en frontend/package.json.
[tools]
java = "temurin-25"
vite-plus = "1.3.0"

# ---------------------------------------------------------------------------
# Tareas: `mise tasks` las lista, `mise run <tarea>` las ejecuta.
# ---------------------------------------------------------------------------

[tasks.install]
description = "Instala las dependencias del frontend"
dir = "frontend"
run = "vp install"

[tasks."dev:backend"]
description = "Arranca el backend en :8080 (levanta Postgres con docker compose)"
dir = "backend"
run = "./mvnw spring-boot:run"

[tasks."dev:frontend"]
description = "Arranca el dev server de Vite+ en :5173 (/api va al backend)"
dir = "frontend"
depends = ["install"]
run = "vp dev"

[tasks.dev]
description = "Backend + frontend en paralelo"
depends = ["dev:backend", "dev:frontend"]

[tasks."test:backend"]
description = "Tests del backend (Testcontainers; también regenera backend/openapi.json)"
dir = "backend"
run = "./mvnw -B test"

[tasks."test:frontend"]
description = "Tests del frontend con vp test"
dir = "frontend"
depends = ["install"]
run = "vp test"

[tasks.test]
description = "Todos los tests"
depends = ["test:backend", "test:frontend"]

[tasks."api:generate"]
description = "Regenera backend/openapi.json desde los tests y los tipos TS del cliente"
depends = ["install"]
run = [
  "cd backend && ./mvnw -B -q test -Dtest=OpenApiDocumentTest",
  "cd frontend && vp run api:generate",
]

[tasks.fmt]
description = "Formatea el código: backend con Spotless (palantir-java-format) y frontend con vp fmt"
depends = ["install"]
run = [
  "cd backend && ./mvnw -B -q spotless:apply",
  "cd frontend && vp fmt",
]

[tasks."db:down"]
description = "Para el Postgres de desarrollo (los datos se conservan en el volumen)"
run = "docker compose down"

[tasks.check]
description = "Lo que correría CI: tests, check, build y contrato del API al día"
run = [
  "cd frontend && vp install --frozen-lockfile",
  # `verify` también ejecuta spotless:check (formato del código Java).
  "cd backend && ./mvnw -B verify",
  "cd frontend && vp run api:generate",
  "cd frontend && vp check",
  "cd frontend && vp test",
  "cd frontend && vp build",
  # Si falla, hay ficheros generados que no se han commiteado (ejecuta `mise run api:generate`).
  "git diff --exit-code -- backend/openapi.json frontend/src/api/generated frontend/src/routeTree.gen.ts",
]
```

Desde la raíz:

```sh
mise trust
mise install
mise run dev
```

`dev` depende de dos tareas que arrancan los servidores. `dev:frontend` instala sus dependencias antes de arrancar. El backend levanta PostgreSQL mediante Compose. Abre `http://localhost:5173`.

En otra terminal puedes ejecutar `mise run test`, `mise run api:generate` y `mise run fmt`. `mise run db:down` detiene el PostgreSQL de desarrollo y conserva los datos.

## 6. Comprueba los ficheros temporales

El proyecto final no necesita `HelloController.java`, `App.tsx`, `App.css` ni logos de la plantilla. Confirma que ya no quedan imports que dependan de ellos.

En el backend debes tener `TodoApplication`, las clases del paquete `todos`, las clases de `web`, los tres `package-info.java`, la migración y los cuatro ficheros de test. En el frontend debes tener las dos rutas, el árbol generado, los componentes, los módulos de API y el cliente generado.

`TodoController` ya no contiene una colección en memoria. `TodoService` no hace `save` para actualizar una entidad gestionada. El frontend ya no mantiene una copia manual del DTO `Todo` ni construye a mano las URLs en cada componente.

## 7. Ejecuta la comprobación completa

Primero genera y formatea:

```sh
mise run api:generate
mise run fmt
```

Revisa los cambios con `git diff`. Guarda tu punto de control con Git, incluidos los ficheros generados, antes de ejecutar el control de sincronización:

```sh
git add .
git commit -m "Completa la aplicación TODO del tutorial"
mise run check
```

`check` instala con el lockfile, verifica el backend, regenera el cliente, comprueba el frontend, ejecuta sus tests y construye la aplicación. Al final exige que la regeneración no haya cambiado los artefactos versionados. Si acabas de modificar la API y aún no has guardado sus artefactos, esa última comparación fallará aunque el código compile.

La documentación de esta guía no introduce por sí sola un workflow de CI remoto. La tarea `check` reúne las comprobaciones locales que podrían ejecutarse en CI.

## 8. Recorre el resultado como usuario

Comprueba lo siguiente con los dos servidores abiertos:

- Una lista inicialmente vacía muestra su mensaje.
- Crear una tarea devuelve un identificador, fechas y versión, y vacía el formulario.
- Un título vacío muestra el error del servidor y no crea una fila.
- Editar con una versión actual guarda el título y actualiza la lista.
- Una segunda edición con una versión antigua devuelve 409 sin sobrescribir.
- Completar y descompletar cambia el checkbox y sincroniza los filtros.
- Borrar elimina la fila; consultar después ese UUID devuelve 404.
- Recargar una URL con filtro conserva el filtro y atrás recupera la navegación anterior.
- Reiniciar Java conserva los datos de desarrollo.
- Detener Java provoca un aviso legible; tras arrancarlo puedes reintentar.

## 9. Explica una operación de principio a fin

Toma un caso que ya has ejecutado, por ejemplo editar. El input conserva un borrador en React. La mutación manda título y versión mediante el cliente generado. El proxy reenvía PATCH al backend. El controlador convierte y valida el cuerpo. El servicio transaccional busca la entidad, compara versiones y cambia su estado. Hibernate escribe los cambios y el DTO devuelve el resultado. Query invalida las listas y la ruta observa su caché actualizada.

Si puedes localizar cada paso en un fichero y describir qué ocurre cuando falla, has llegado al resultado con una explicación del funcionamiento, además del código.

[Anterior](15-interfaz-y-tests-frontend.md) · [Índice](README.md)
