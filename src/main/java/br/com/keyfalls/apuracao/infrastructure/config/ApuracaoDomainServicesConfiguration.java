package br.com.keyfalls.apuracao.infrastructure.config;

import br.com.keyfalls.apuracao.application.ApuracaoApplicationService;
import br.com.keyfalls.apuracao.application.ApuracaoUseCases;
import br.com.keyfalls.apuracao.application.ConsultaMatrizApuracaoPort;
import br.com.keyfalls.apuracao.application.ControleAnulacaoProcessadaPort;
import br.com.keyfalls.apuracao.application.FichaRespostaFileParserPort;
import br.com.keyfalls.apuracao.application.RecalculoPorQuestaoAnuladaHandler;
import br.com.keyfalls.apuracao.domain.CalculadoraDeNota;
import br.com.keyfalls.apuracao.domain.ClassificadorDeCandidatos;
import br.com.keyfalls.apuracao.domain.FichaDeRespostaRepository;
import br.com.keyfalls.apuracao.domain.ResultadoRepository;
import br.com.keyfalls.shared.application.event.EventoProcessadoRepository;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Configuracao de Beans para o Bounded Context Apuracao.
 * Registra os Domain Services puros (CalculadoraDeNota, ClassificadorDeCandidatos),
 * o Handler de Recalculo por Questao Anulada e o Use Case transacional ApuracaoUseCases.
 */
@Configuration
public class ApuracaoDomainServicesConfiguration {

    @Bean
    public CalculadoraDeNota calculadoraDeNota() {
        return new CalculadoraDeNota();
    }

    @Bean
    public ClassificadorDeCandidatos classificadorDeCandidatos() {
        return new ClassificadorDeCandidatos();
    }

    @Bean
    public RecalculoPorQuestaoAnuladaHandler recalculoPorQuestaoAnuladaHandler(
        FichaDeRespostaRepository fichaRepository,
        ResultadoRepository resultadoRepository,
        ConsultaMatrizApuracaoPort consultaMatrizPort,
        EventoProcessadoRepository eventoProcessadoRepository,
        ControleAnulacaoProcessadaPort controleAnulacaoPort,
        CalculadoraDeNota calculadoraDeNota,
        ClassificadorDeCandidatos classificadorDeCandidatos,
        DomainEventPublisher eventPublisher
    ) {
        return new RecalculoPorQuestaoAnuladaHandler(
            fichaRepository,
            resultadoRepository,
            consultaMatrizPort,
            eventoProcessadoRepository,
            controleAnulacaoPort,
            calculadoraDeNota,
            classificadorDeCandidatos,
            eventPublisher,
            Clock.systemUTC()
        );
    }

    @Bean
    public ApuracaoUseCases apuracaoUseCases(
        FichaDeRespostaRepository fichaRepository,
        ResultadoRepository resultadoRepository,
        ConsultaMatrizApuracaoPort consultaMatrizPort,
        FichaRespostaFileParserPort fileParserPort,
        CalculadoraDeNota calculadoraDeNota,
        ClassificadorDeCandidatos classificadorDeCandidatos,
        DomainEventPublisher eventPublisher,
        TransactionOperations transactionOperations
    ) {
        ApuracaoApplicationService delegate = new ApuracaoApplicationService(
            fichaRepository,
            resultadoRepository,
            consultaMatrizPort,
            fileParserPort,
            calculadoraDeNota,
            classificadorDeCandidatos,
            eventPublisher
        );

        return new ApuracaoUseCases() {
            @Override
            public ImportacaoFichasOutput importarFichasOpticas(ImportarFichasOpticasCommand command) {
                return transactionOperations.execute(status -> delegate.importarFichasOpticas(command));
            }

            @Override
            public DispararApuracaoOutput processarApuracaoEClassificacao(UUID concursoId) {
                return transactionOperations.execute(status -> delegate.processarApuracaoEClassificacao(concursoId));
            }

            @Override
            public List<ClassificadoItemOutput> listarClassificados(UUID concursoId, UUID cargoIdOpcional) {
                return delegate.listarClassificados(concursoId, cargoIdOpcional);
            }

            @Override
            public BoletimDesempenhoCandidatoOutput consultarMeuDesempenho(
                UUID candidatoAutenticadoId,
                UUID concursoId
            ) {
                return delegate.consultarMeuDesempenho(candidatoAutenticadoId, concursoId);
            }

            @Override
            public GabaritoVigenciaOutput consultarGabaritoVigente(UUID provaId) {
                return delegate.consultarGabaritoVigente(provaId);
            }
        };
    }
}
