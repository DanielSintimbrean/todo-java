# 02. Hello world desde el backend

Vas a crear una URL que devuelve un texto escrito en Java. Todavía no interviene React.

## 1. Crea el controlador

Crea `backend/src/main/java/com/example/todo/HelloController.java`:

```java
package com.example.todo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    @GetMapping("/api/hello")
    public String hello() {
        return "Hello world desde Java";
    }
}
```

La primera línea declara el paquete. La ruta de carpetas reproduce ese paquete. `public class` declara una clase accesible desde otros paquetes y su nombre coincide con el del fichero.

`hello()` es un método que devuelve `String`, el tipo de texto de Java. Los paréntesis están vacíos porque no recibe argumentos.

Las anotaciones empiezan con `@`. `@RestController` permite a Spring registrar esta clase para atender peticiones y escribir los valores devueltos en la respuesta HTTP. `@GetMapping` asocia el método con una petición GET a una ruta concreta.

## 2. Arranca el servidor

En una terminal, desde la raíz:

```sh
cd backend
mise exec -- ./mvnw spring-boot:run
```

Déjalo abierto. El proceso debe indicar que ha arrancado en el puerto 8080.

En otra terminal:

```sh
curl -i http://localhost:8080/api/hello
```

Debes recibir un estado `200` y el cuerpo:

```text
Hello world desde Java
```

También puedes abrir esa URL directamente en el navegador. El cuerpo es texto; no llames aún a `response.json()` desde el frontend.

## 3. Sigue una petición

`curl` abre una conexión a `localhost:8080` y envía `GET /api/hello`. El servidor embebido recibe la petición y Spring busca un método asociado a esa ruta. Ejecuta `hello()` y usa el `String` devuelto como cuerpo de la respuesta.

La clase no necesita un `main` propio. Spring crea el controlador cuando arranca `TodoApplication`. En capítulos posteriores esa creación automática permitirá conectar controladores, servicios y repositorios.

## 4. Haz un cambio pequeño

Cambia el texto por `Hola, estoy aprendiendo Java`. Reinicia el backend y repite el `curl`. El objetivo es comprobar que sabes qué fichero cambia lo que devuelve esa URL.

Puedes dejar ese texto o restaurar el original. No cambia el siguiente capítulo.

## Comprobación

`GET /api/hello` devuelve tu mensaje con estado 200. Una ruta inventada, como `/api/no-existe`, devuelve 404. Ese 404 confirma que el servidor responde aunque no haya un método para esa ruta.

Si `/api/hello` devuelve 404, revisa que el paquete sea `com.example.todo` o uno de sus subpaquetes y que hayas reiniciado. Si aparece `Connection refused`, el proceso no está escuchando en ese puerto.

Referencia oficial para esta estructura mínima: [primera aplicación con Spring Boot](https://docs.spring.io/spring-boot/4.0/tutorial/first-application/index.html).

[Anterior](01-configuracion.md) · [Índice](README.md) · [Siguiente: hello world desde el frontend](03-hello-frontend.md)
