package br.com.keyfalls.logistica.infrastructure.config;

import br.com.keyfalls.logistica.application.ConsultaCandidatosHomologadosPort;
import br.com.keyfalls.logistica.application.LogisticaApplicationService;
import br.com.keyfalls.logistica.application.LogisticaUseCases;
import br.com.keyfalls.logistica.domain.AlocacaoDeSalaRepository;
import br.com.keyfalls.logistica.domain.AlocadorDeSalas;
import br.com.keyfalls.logistica.domain.BairroCidadeMatchingStrategy;
import br.com.keyfalls.logistica.domain.LocalDeProvaRepository;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

/**
 * Configuracao de Beans para o Bounded Context Logistica.
 * Registra o Domain Service puro AlocadorDeSalas e o Use Case transacional LogisticaUseCases.
 */
@Configuration
public class LogisticaBeanConfiguration {

    @Bean
    public AlocadorDeSalas alocadorDeSalas() {
        return new AlocadorDeSalas(new BairroCidadeMatchingStrategy());
    }

    @Bean
    public LogisticaUseCases logisticaUseCases(
        LocalDeProvaRepository localDeProvaRepository,
        AlocacaoDeSalaRepository alocacaoDeSalaRepository,
        ConsultaCandidatosHomologadosPort consultaCandidatosHomologadosPort,
        AlocadorDeSalas alocadorDeSalas,
        DomainEventPublisher eventPublisher,
        TransactionOperations transactionOperations
    ) {
        LogisticaApplicationService delegate = new LogisticaApplicationService(
            localDeProvaRepository,
            alocacaoDeSalaRepository,
            consultaCandidatosHomologadosPort,
            alocadorDeSalas,
            eventPublisher
        );

        return new LogisticaUseCases() {
            @Override
            public LocalDeProvaCriadoOutput cadastrarLocalSalasEFiscais(CadastrarLocalDeProvaCommand command) {
                return transactionOperations.execute(status -> delegate.cadastrarLocalSalasEFiscais(command));
            }

            @Override
            public AlocacaoEmLoteOutput executarAlocacaoPorProximidade(UUID concursoId) {
                return transactionOperations.execute(status -> delegate.executarAlocacaoPorProximidade(concursoId));
            }

            @Override
            public ListaPresencaRegistradaOutput registrarListaDePresenca(RegistrarListaPresencaCommand command) {
                return transactionOperations.execute(status -> delegate.registrarListaDePresenca(command));
            }
        };
    }
}
