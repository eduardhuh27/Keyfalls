package br.com.keyfalls.shared.infrastructure.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerJwt";

    @Bean
    public OpenAPI keyFallsOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("KeyFalls API — Gestao de Concursos Publicos")
                .version("v1")
                .description("Monolito Modular DDD + Clean/Hexagonal Architecture (Java 21 & Spring Boot 3)"))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME_NAME, new SecurityScheme()
                    .name(BEARER_SCHEME_NAME)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
