package com.example.todo.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Todo API").version("v1"))
                // Servidor fijo: si no, springdoc pone http://localhost:<puerto> y openapi.json cambiaría en cada test.
                .servers(List.of(new Server().url("/")));
    }
}
