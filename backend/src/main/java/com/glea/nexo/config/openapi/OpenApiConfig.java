package com.glea.nexo.config.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI nexoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Glea Nexo API")
                        .version("v1")
                        .description("Backend API para Glea Nexo (ingesta + inventario). Temporal contract: telemetry readings require ts as event time in ISO-8601 UTC; query parameters from/to are optional but when both are present they must satisfy from <= to and must not exceed 2 years.")
                        .contact(new Contact().name("Glea Nexo Team")));
    }
}
