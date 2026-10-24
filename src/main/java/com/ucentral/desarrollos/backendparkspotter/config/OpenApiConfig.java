package com.ucentral.desarrollos.backendparkspotter.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación interactiva de la API (Sprint 04, T4-01): /swagger-ui.html y el JSON en /v3/api-docs.
 * El botón "Authorize" recibe el access token del login (sin la palabra Bearer).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI parkSpotterOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ParkSpotter API")
                        .version("v1")
                        .description("API REST de ParkSpotter para los fronts web y Android. Guía completa en docs/API-Referencia.md."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
