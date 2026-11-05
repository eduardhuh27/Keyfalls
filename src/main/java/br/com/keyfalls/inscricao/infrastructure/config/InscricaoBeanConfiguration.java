package br.com.keyfalls.inscricao.infrastructure.config;

import br.com.keyfalls.inscricao.application.AvaliarIsencaoUseCase;
import br.com.keyfalls.inscricao.application.ConsultaOfertaConcursoPort;
import br.com.keyfalls.inscricao.application.EmissorBoletoBancarioPort;
import br.com.keyfalls.inscricao.application.GeradorNumeroInscricaoPort;
import br.com.keyfalls.inscricao.application.InscricaoConsultasEIsencaoApplicationService;
import br.com.keyfalls.inscricao.application.InscricaoConsultasEIsencaoUseCases;
import br.com.keyfalls.inscricao.application.InscricaoRealizadaOutput;
import br.com.keyfalls.inscricao.application.RealizarInscricaoCommand;
import br.com.keyfalls.inscricao.application.RealizarInscricaoUseCase;
import br.com.keyfalls.inscricao.domain.InscricaoRepository;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;

/**
 * Configuracao Spring na camada de infraestrutura responsavel por montar o grafo
 * de objetos de Application e Domain Services sem poluir domain/application com
 * @Service, @Component ou @Transactional.
 */
@Configuration
public class InscricaoBeanConfiguration {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RealizarInscricaoUseCase realizarInscricaoUseCase(
        InscricaoRepository inscricaoRepository,
        ConsultaOfertaConcursoPort consultaOfertaConcursoPort,
        GeradorNumeroInscricaoPort geradorNumeroInscricaoPort,
        EmissorBoletoBancarioPort emissorBoletoBancarioPort,
        DomainEventPublisher eventPublisher,
        Clock clock,
        TransactionOperations transactionOperations
    ) {
        // Decorator transacional programatico: garante atomicidade de persistencia + registro de eventos
        // mantendo RealizarInscricaoUseCase 100% livre de anotacoes do Spring!
        return new RealizarInscricaoUseCase(
            inscricaoRepository,
            consultaOfertaConcursoPort,
            geradorNumeroInscricaoPort,
            emissorBoletoBancarioPort,
            eventPublisher,
            clock
        ) {
            @Override
            public InscricaoRealizadaOutput executar(RealizarInscricaoCommand command) {
                return transactionOperations.execute(status -> super.executar(command));
            }
        };
    }

    @Bean
    public AvaliarIsencaoUseCase avaliarIsencaoUseCase(
        InscricaoRepository inscricaoRepository,
        DomainEventPublisher eventPublisher,
        Clock clock
    ) {
        return new AvaliarIsencaoUseCase(inscricaoRepository, eventPublisher, clock);
    }

    @Bean
    public InscricaoConsultasEIsencaoUseCases inscricaoConsultasEIsencaoUseCases(
        InscricaoRepository inscricaoRepository,
        ConsultaOfertaConcursoPort consultaOfertaConcursoPort,
        DomainEventPublisher eventPublisher,
        Clock clock,
        TransactionOperations transactionOperations
    ) {
        var delegate = new InscricaoConsultasEIsencaoApplicationService(
            inscricaoRepository, consultaOfertaConcursoPort, eventPublisher, clock
        );
        return new InscricaoConsultasEIsencaoUseCases() {
            @Override
            public BoletoOutput consultarBoleto( java.util.UUID inscricaoId, java.util.UUID solicitanteId, boolean isAdmin) {
                return transactionOperations.execute(status -> delegate.consultarBoleto(inscricaoId, solicitanteId, isAdmin));
            }

            @Override
            public IsencaoOutput solicitarIsencao(SolicitarIsencaoCommand command) {
                return transactionOperations.execute(status -> delegate.solicitarIsencao(command));
            }

            @Override
            public IsencaoOutput consultarIsencao(java.util.UUID isencaoId, java.util.UUID solicitanteId, boolean isAdmin) {
                return transactionOperations.execute(status -> delegate.consultarIsencao(isencaoId, solicitanteId, isAdmin));
            }
        };
    }
}
