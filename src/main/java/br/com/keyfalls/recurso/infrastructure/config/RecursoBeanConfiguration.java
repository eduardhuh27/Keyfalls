package br.com.keyfalls.recurso.infrastructure.config;

import br.com.keyfalls.recurso.application.HomologacaoConcursoPort;
import br.com.keyfalls.recurso.application.RecursoApplicationService;
import br.com.keyfalls.recurso.application.RecursoUseCases;
import br.com.keyfalls.recurso.domain.RecursoRepository;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

/**
 * Configuracao de Beans para o Bounded Context Recurso.
 */
@Configuration
public class RecursoBeanConfiguration {

    @Bean
    public RecursoUseCases recursoUseCases(
        RecursoRepository recursoRepository,
        HomologacaoConcursoPort homologacaoConcursoPort,
        DomainEventPublisher eventPublisher,
        TransactionOperations transactionOperations
    ) {
        RecursoApplicationService delegate = new RecursoApplicationService(
            recursoRepository,
            homologacaoConcursoPort,
            eventPublisher
        );

        return new RecursoUseCases() {
            @Override
            public RecursoProtocoladoOutput abrirRecurso(AbrirRecursoCommand command) {
                return transactionOperations.execute(status -> delegate.abrirRecurso(command));
            }

            @Override
            public RecursoJulgadoOutput julgarRecurso(JulgarRecursoCommand command) {
                return transactionOperations.execute(status -> delegate.julgarRecurso(command));
            }

            @Override
            public ResultadoFinalHomologadoOutput homologarResultadoFinal(UUID concursoId) {
                return transactionOperations.execute(status -> delegate.homologarResultadoFinal(concursoId));
            }
        };
    }
}
