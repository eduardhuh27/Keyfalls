package br.com.keyfalls.identidade.infrastructure.config;

import br.com.keyfalls.identidade.application.AuthApplicationService;
import br.com.keyfalls.identidade.application.AuthUseCases;
import br.com.keyfalls.identidade.domain.CandidatoRepository;
import br.com.keyfalls.shared.infrastructure.security.JwtTokenService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;

@Configuration
public class IdentidadeBeanConfiguration {

    @Bean
    public AuthUseCases.HashSenhaPort hashSenhaPort(PasswordEncoder passwordEncoder) {
        return new AuthUseCases.HashSenhaPort() {
            @Override
            public String gerarHash(String senhaPura) {
                return passwordEncoder.encode(senhaPura);
            }

            @Override
            public boolean verificar(String senhaPura, String senhaHash) {
                return passwordEncoder.matches(senhaPura, senhaHash);
            }
        };
    }

    @Bean
    public AuthUseCases.EmissorTokenJwtPort emissorTokenJwtPort(JwtTokenService jwtTokenService) {
        return jwtTokenService::emitirTokenAcesso;
    }

    @Bean
    public AuthUseCases authUseCases(
        CandidatoRepository candidatoRepository,
        AuthUseCases.HashSenhaPort hashSenhaPort,
        AuthUseCases.EmissorTokenJwtPort emissorTokenJwtPort,
        Clock clock,
        TransactionOperations transactionOperations
    ) {
        AuthApplicationService delegate = new AuthApplicationService(
            candidatoRepository, hashSenhaPort, emissorTokenJwtPort, clock
        );
        return new AuthUseCases() {
            @Override
            public CandidatoRegistradoOutput registrarCandidato(RegistrarCandidatoCommand command) {
                return transactionOperations.execute(status -> delegate.registrarCandidato(command));
            }

            @Override
            public TokenAutenticacaoOutput autenticar(LoginCommand command) {
                return transactionOperations.execute(status -> delegate.autenticar(command));
            }
        };
    }
}
