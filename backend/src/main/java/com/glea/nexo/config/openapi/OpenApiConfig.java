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
                        .description("Backend API para Glea Nexo (ingesta + inventario)")
                        .contact(new Contact().name("Glea Nexo Team")));
    }
}
