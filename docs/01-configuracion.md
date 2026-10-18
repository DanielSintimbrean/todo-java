# 01. Configuración básica del backend y del frontend

El objetivo es tener una carpeta para Java y otra para React. Al terminar podrás compilar el backend y arrancar el servidor de desarrollo del frontend. Aún no habrá comunicación entre ellos.

## 1. Prepara las herramientas

Necesitas Git, `curl`, `unzip` y [mise](https://mise.jdx.dev/getting-started.html). Los comandos de esta guía usan Bash en Linux o macOS. Docker será necesario en el capítulo 8.

Comprueba lo que tienes instalado:

```sh
git --version
curl --version
unzip -v
mise --version
```

No necesitas instalar Maven globalmente. El backend tendrá un Maven Wrapper, que descarga la versión de Maven definida en el proyecto.

## 2. Crea una carpeta vacía

Desde una carpeta donde guardes tus proyectos:

```sh
mkdir todo-java-aprendizaje
cd todo-java-aprendizaje
git init
```

Crea `mise.toml`:

```toml
[plugins]
vite-plus = "https://github.com/markmals/mise-vite-plus"

[tools]
java = "temurin-25"
vite-plus = "1.3.0"
```

Desde la raíz:

```sh
mise trust
mise install
mise exec -- java -version
mise exec -- vp --version
```

Usaremos `mise exec --` en los primeros comandos para que funcionen aunque no tengas mise activado en tu shell. Si lo tienes activado, puedes escribir directamente `java` y `vp`.

Java ejecuta el backend. `vp` es la CLI de Vite+ y gestiona las herramientas del frontend. El plugin de mise es el mismo que usa el repositorio. Node ejecutará las herramientas de construcción; el JavaScript de la aplicación se ejecutará en el navegador.

## 3. Genera el esqueleto Java

Genera el backend con Spring Boot CLI desde la raíz. `mise exec` descarga y usa la CLI solo para este comando; no hace falta añadirla a `mise.toml`:

```sh
mise exec spring-boot@4.1.1 -- spring init \
  --build=maven \
  --boot-version=4.1.1 \
  --java-version=25 \
  --group-id=com.example \
  --artifact-id=todo \
  --name=todo \
  --package-name=com.example.todo \
  --dependencies=web \
  backend
```

El comando crea el proyecto extraído en `backend/`. Si Spring Initializr ya no ofrece Boot 4.1.1, genera uno de Boot 4 con Java 25 y ajusta el `pom.xml` al siguiente antes de compilar. Conserva `mvnw`, `mvnw.cmd` y `.mvn/` que se hayan generado. Sustituye `backend/pom.xml` por esta versión mínima:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>
    <groupId>com.example</groupId>
    <artifactId>todo</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <properties>
        <java.version>25</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

El `parent` aporta versiones compatibles y configuración compartida. Una `dependency` incorpora una biblioteca. Un `plugin` ejecuta una tarea de construcción. Aquí el starter web aporta Spring MVC y un servidor HTTP embebido.

Elimina por ahora el test de ejemplo que Initializr haya creado en `src/test/java/`. Nuestro POM mínimo todavía no declara dependencias de test. Las añadiremos al escribir tests propios.

Comprueba que `backend/src/main/java/com/example/todo/TodoApplication.java` contiene:

```java
package com.example.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TodoApplication {
    public static void main(String[] args) {
        SpringApplication.run(TodoApplication.class, args);
    }
}
```

Deja en `backend/src/main/resources/application.properties` solo:

```properties
spring.application.name=todo
```

`main` es el punto de entrada de Java. Spring Boot crea el contexto de la aplicación y arranca el servidor. Las clases que pondremos bajo `com.example.todo` estarán dentro de su búsqueda de componentes.

Desde la raíz:

```sh
cd backend
chmod +x mvnw
mise exec -- ./mvnw compile
cd ..
```

Debes ver `BUILD SUCCESS`. La primera ejecución descarga Maven y dependencias.

## 4. Genera el esqueleto React

Desde la raíz:

```sh
mise exec -- vp create vite --directory frontend --package-manager pnpm --no-git --no-hooks --no-agent --no-editor -- --template react-ts
cd frontend
mise exec -- vp install
mise exec -- vp dev
```

El comando de creación y sus opciones están descritos en la [documentación de Vite+](https://viteplus.dev/guide/create). Si el generador pregunta, confirma React con TypeScript y pnpm. Los valores por defecto del generador pueden cambiar; las versiones finales del repositorio se fijan en el capítulo 16.

Abre la dirección que indique Vite+, normalmente `http://localhost:5173`. De momento basta con ver la plantilla. Detén el servidor con Ctrl+C.

## 5. Identifica las carpetas

Debes tener `mise.toml`, `backend/` y `frontend/` en la raíz. Dentro del backend, `src/main/java` guarda Java y `src/main/resources` guarda configuración. Dentro del frontend, `src/` guarda los componentes y `index.html` contiene el elemento donde React se monta.

Añade `.gitignore` en la raíz:

```gitignore
backend/target/
frontend/node_modules/
frontend/dist/
```

El código y los lockfiles se guardan en Git. Las dependencias descargadas y los resultados de compilación se reconstruyen.

## Comprobación

El backend compila y la plantilla del frontend abre en el navegador. Si Maven dice `release version 25 not supported`, comprueba `mise exec -- java -version`. Si Vite abre `:5174`, el puerto `:5173` ya está ocupado.

No añadas todavía JPA, PostgreSQL, TanStack ni generación de clientes. El siguiente paso necesita únicamente un servidor capaz de responder texto.

[Índice](README.md) · [Siguiente: hello world desde el backend](02-hello-backend.md)
