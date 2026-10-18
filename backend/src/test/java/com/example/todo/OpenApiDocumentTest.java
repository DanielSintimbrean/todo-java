package com.example.todo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Genera {@code backend/openapi.json}, el contrato del que sale el cliente TypeScript del frontend.
 * Se versiona en git: {@code mise run check} falla si el fichero cambia y no se ha commiteado.
 */
@IntegrationTest
class OpenApiDocumentTest {

    @Autowired
    RestTestClient client;

    @Test
    void writesOpenApiDocument() throws IOException {
        String json = client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        // Maven ejecuta los tests con backend/ como directorio de trabajo.
        Files.writeString(Path.of("openapi.json"), json.strip() + "\n");
    }
}
