package br.com.keyfalls.concurso.infrastructure.config;

import br.com.keyfalls.concurso.application.ConcursoApplicationService;
import br.com.keyfalls.concurso.application.ConcursoUseCases;
import br.com.keyfalls.concurso.domain.ConcursoRepository;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;

@Configuration
public class ConcursoBeanConfiguration {

    @Bean
    public ConcursoUseCases concursoUseCases(
        ConcursoRepository concursoRepository,
        DomainEventPublisher eventPublisher,
        Clock clock,
        TransactionOperations transactionOperations
    ) {
        ConcursoApplicationService delegate = new ConcursoApplicationService(concursoRepository, eventPublisher, clock);
        return new ConcursoUseCases() {
            @Override
            public ConcursoCriadoOutput criarConcurso(CriarConcursoCommand command) {
                return transactionOperations.execute(status -> delegate.criarConcurso(command));
            }

            @Override
            public CargoComProvasOutput adicionarCargoEProvas(AdicionarCargoComProvasCommand command) {
                return transactionOperations.execute(status -> delegate.adicionarCargoEProvas(command));
            }

            @Override
            public QuestoesCadastradasOutput cadastrarQuestoes(CadastrarQuestoesCommand command) {
                return transactionOperations.execute(status -> delegate.cadastrarQuestoes(command));
            }

            @Override
            public GabaritoPublicadoOutput publicarOuAtualizarGabarito(PublicarGabaritoCommand command) {
                return transactionOperations.execute(status -> delegate.publicarOuAtualizarGabarito(command));
            }
        };
    }
}
