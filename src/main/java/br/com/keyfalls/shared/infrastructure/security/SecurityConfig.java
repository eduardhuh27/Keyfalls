package br.com.keyfalls.shared.infrastructure.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        return http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // OpenAPI / Swagger & Healthcheck
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/actuator/health"
                ).permitAll()

                // Endpoints Publicos de Consulta Oficial e Autenticacao
                .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/registrar", "/candidatos").permitAll()
                .requestMatchers(HttpMethod.GET, "/concursos/*/classificados", "/provas/*/gabarito").permitAll()

                // Contexto Concurso (ADMIN)
                .requestMatchers(HttpMethod.POST, "/concursos", "/concursos/*/cargos", "/provas/*/questoes").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/provas/*/gabarito").hasRole("ADMIN")

                // Contexto Inscricao (CANDIDATO / ADMIN)
                .requestMatchers(HttpMethod.POST, "/inscricoes", "/inscricoes/*/isencao").hasRole("CANDIDATO")
                .requestMatchers(HttpMethod.GET, "/inscricoes/*/boleto", "/isencoes/*").hasAnyRole("CANDIDATO", "ADMIN")

                // Contexto Logistica (FISCAL e ADMIN)
                .requestMatchers(HttpMethod.POST, "/salas/*/presencas").hasAnyRole("FISCAL", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/concursos/*/locais", "/concursos/*/alocacoes").hasRole("ADMIN")

                // Contexto Apuracao e Resultados
                .requestMatchers(HttpMethod.GET, "/candidatos/me/desempenho").hasRole("CANDIDATO")
                .requestMatchers(HttpMethod.POST, "/fichas/importacao", "/concursos/*/apuracao").hasRole("ADMIN")

                // Contexto Recursos
                .requestMatchers(HttpMethod.POST, "/recursos").hasRole("CANDIDATO")
                .requestMatchers(HttpMethod.PUT, "/recursos/*/julgamento").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/concursos/*/resultado-final").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            )
            .build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return jwtConverter;
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey secretKey = buildHmacSecretKey(properties.secret());
        return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        SecretKey secretKey = buildHmacSecretKey(properties.secret());
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.addAllowedOrigin("null");
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static SecretKey buildHmacSecretKey(String rawSecret) {
        return new SecretKeySpec(rawSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
