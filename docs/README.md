# Recrear todo-java para aprender Java

Construirás la aplicación desde una carpeta vacía. Primero levantarás dos procesos y conseguirás que el navegador lea un mensaje de Java. Después añadirás tareas, persistencia y las herramientas que usa este repositorio.

El orden es una propuesta de aprendizaje. No reproduce los commits del proyecto. Cada capítulo parte del anterior y termina con una comprobación observable.

## Recorrido

1. [Configuración básica del backend y del frontend](01-configuracion.md).
2. [Hello world desde Java y Spring Boot](02-hello-backend.md).
3. [Hello world desde React](03-hello-frontend.md).
4. [Primera petición del frontend al backend con fetch](04-primera-request.md).
5. [Primeros TODOs en memoria y records de Java](05-todos-en-memoria.md).
6. [Listar y crear tareas desde React con fetch](06-todos-desde-react.md).
7. [Validación y respuestas de error](07-validacion-y-errores.md).
8. [PostgreSQL, Flyway, JPA y transacciones](08-persistencia.md).
9. [Consultar, editar, completar, borrar y filtrar](09-crud-y-filtros.md).
10. [Versiones, concurrencia y Java moderno](10-concurrencia-y-java-moderno.md).
11. [Tests de integración con PostgreSQL real](11-tests-backend.md).
12. [OpenAPI y cliente TypeScript generado](12-openapi-y-cliente.md).
13. [TanStack Query y actualización optimista](13-tanstack-query.md).
14. [TanStack Router y filtros en la URL](14-tanstack-router.md).
15. [Interfaz final y tests de frontend](15-interfaz-y-tests-frontend.md).
16. [Tooling final y comprobación de la reconstrucción](16-tooling-y-verificacion.md).

## Cómo usar los capítulos

Trabaja en otra carpeta, por ejemplo `todo-java-aprendizaje`. Este repositorio es la referencia del resultado final. Así podrás comparar tu implementación sin sobrescribirla con los ejemplos intermedios.

Las rutas de los bloques son relativas a la raíz de tu proyecto nuevo. Cada comando indica cuándo debes entrar en `backend/` o `frontend/`. Deja los servidores abiertos en terminales separadas y usa otra para `curl`. Cuando cambies Java, detén el backend con Ctrl+C y arráncalo otra vez. Todavía no hemos añadido recarga automática.

Cuando un capítulo muestre un fichero completo, créalo o reemplaza su versión anterior. Cuando diga "añade este método", conserva el resto del fichero. Los ficheros enlazados a `../backend/` o `../frontend/` muestran el código final de referencia. No los incorpores antes del capítulo que introduce sus dependencias.

No necesitas conocer Spring, JPA o Maven para empezar. Sí conviene saber qué son una variable, una función y una condición. Explicaremos las particularidades de Java al usarlas. El frontend usa React y TypeScript porque son los del proyecto; el centro del recorrido es aprender Java y entender qué hace el servidor.

## Decisiones del recorrido

Durante los primeros capítulos solo usaremos el starter web en el backend y React en el frontend. Las llamadas HTTP usarán `fetch`, que ya existe en el navegador. PostgreSQL llega en el capítulo 8 y el cliente generado llega en el 12.

Las versiones de referencia son las declaradas en este checkout: Java 25, Spring Boot 4.1.1 y Vite+ 1.3.0. Para el resultado exacto, consulta también [pom.xml](../backend/pom.xml), [package.json](../frontend/package.json) y [pnpm-workspace.yaml](../frontend/pnpm-workspace.yaml). No presupongas que el generador de proyectos seguirá eligiendo esas versiones por defecto.

Las razones pedagógicas de este orden son recomendaciones de esta guía. Los detalles del funcionamiento final se apoyan en el código del repositorio. Una simplificación temporal siempre se señala antes de sustituirla.

## Qué habrás construido al terminar

Una aplicación que guarda tareas en PostgreSQL, permite crearlas, editarlas, completarlas, borrarlas y filtrarlas. La API valida los datos y detecta ediciones con versiones antiguas. El frontend muestra errores y sincroniza sus listas después de las mutaciones. Los tests y la generación del contrato permiten comprobar cambios futuros.

Empieza por [01. Configuración básica](01-configuracion.md).
