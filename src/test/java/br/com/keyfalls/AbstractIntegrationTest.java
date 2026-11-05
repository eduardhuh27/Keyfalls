package br.com.keyfalls;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Classe base para testes integrados (Repositorios JPA + Flyway + API REST + Seguranca JWT).
 * Utiliza o padrao Singleton Container + @ServiceConnection (Spring Boot 3.1+),
 * autoconfigurando dinamicamente spring.datasource.* contra um PostgreSQL 16 real em Docker.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES_CONTAINER =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("keyfalls_test")
            .withUsername("keyfalls_test")
            .withPassword("keyfalls_test")
            .withReuse(true);

    static {
        POSTGRES_CONTAINER.start();
    }
}
