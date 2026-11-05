package br.com.keyfalls.apuracao.application;

import br.com.keyfalls.apuracao.domain.event.ResultadoRepublicado;
import br.com.keyfalls.apuracao.domain.CalculadoraDeNota;
import br.com.keyfalls.apuracao.domain.ClassificadorDeCandidatos;
import br.com.keyfalls.apuracao.domain.FichaDeResposta;
import br.com.keyfalls.apuracao.domain.FichaDeRespostaRepository;
import br.com.keyfalls.apuracao.domain.Resultado;
import br.com.keyfalls.apuracao.domain.ResultadoRepository;
import br.com.keyfalls.concurso.domain.event.QuestaoAnulada;
import br.com.keyfalls.shared.application.event.EventoProcessadoRepository;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handler de aplicacao para recalculo por anulacao de questao:
 * Consome QuestaoAnulada com dupla camada de idempotencia (tecnica por eventId +
 * semantica por provaId/questaoId), recalcula as notas via CalculadoraDeNota,
 * reclassifica via ClassificadorDeCandidatos e publica ResultadoRepublicado.
 */
public final class RecalculoPorQuestaoAnuladaHandler {

    private static final String NOME_CONSUMIDOR = "apuracao.recalculo-questao-anulada";

    private final FichaDeRespostaRepository fichaRepository;
    private final ResultadoRepository resultadoRepository;
    private final ConsultaMatrizApuracaoPort consultaMatrizPort;
    private final EventoProcessadoRepository eventoProcessadoRepository;
    private final ControleAnulacaoProcessadaPort controleAnulacaoPort;
    private final CalculadoraDeNota calculadoraDeNota;
    private final ClassificadorDeCandidatos classificadorDeCandidatos;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RecalculoPorQuestaoAnuladaHandler(
        FichaDeRespostaRepository fichaRepository,
        ResultadoRepository resultadoRepository,
        ConsultaMatrizApuracaoPort consultaMatrizPort,
        EventoProcessadoRepository eventoProcessadoRepository,
        ControleAnulacaoProcessadaPort controleAnulacaoPort,
        CalculadoraDeNota calculadoraDeNota,
        ClassificadorDeCandidatos classificadorDeCandidatos,
        DomainEventPublisher eventPublisher,
        Clock clock
    ) {
        this.fichaRepository = Objects.requireNonNull(fichaRepository);
        this.resultadoRepository = Objects.requireNonNull(resultadoRepository);
        this.consultaMatrizPort = Objects.requireNonNull(consultaMatrizPort);
        this.eventoProcessadoRepository = Objects.requireNonNull(eventoProcessadoRepository);
        this.controleAnulacaoPort = Objects.requireNonNull(controleAnulacaoPort);
        this.calculadoraDeNota = Objects.requireNonNull(calculadoraDeNota);
        this.classificadorDeCandidatos = Objects.requireNonNull(classificadorDeCandidatos);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public void tratarQuestaoAnulada(QuestaoAnulada evento) {
        Objects.requireNonNull(evento, "Evento QuestaoAnulada obrigatorio.");

        // Camada 1: Idempotencia Tecnica (reentrega at-least-once do mesmo eventId)
        if (eventoProcessadoRepository.jaFoiProcessado(evento.eventId(), NOME_CONSUMIDOR)) {
            return;
        }

        Instant agora = Instant.now(clock);

        // Camada 2: Idempotencia Semantica de Negocio (chave natural provaId + questaoId)
        if (controleAnulacaoPort.anulacaoJaAplicada(evento.provaId(), evento.questaoId())) {
            eventoProcessadoRepository.registrarProcessamento(evento.eventId(), NOME_CONSUMIDOR, agora);
            return;
        }

        var contexto = consultaMatrizPort.obterContextoAtualizadoPorProva(evento.concursoId(), evento.provaId());

        List<FichaDeResposta> fichasAfetadas = fichaRepository.listarPorConcurso(evento.concursoId()).stream()
            .filter(ficha -> ficha.contemProva(evento.provaId()))
            .toList();

        List<Resultado> resultadosRecalculados = new ArrayList<>(fichasAfetadas.size());
        for (FichaDeResposta ficha : fichasAfetadas) {
            Resultado recalculado = calculadoraDeNota.apurarResultado(
                ficha,
                contexto.cargoId(),
                contexto.matrizesAtualizadasDoCargo(),
                contexto.politicaAnulacao()
            );
            resultadosRecalculados.add(recalculado);
        }

        List<ClassificadorDeCandidatos.ItemClassificacao> novaClassificacao =
            classificadorDeCandidatos.classificarAprovados(resultadosRecalculados);

        resultadoRepository.salvarTodos(resultadosRecalculados);
        controleAnulacaoPort.registrarAnulacaoAplicada(evento.provaId(), evento.questaoId(), evento.eventId(), agora);
        eventoProcessadoRepository.registrarProcessamento(evento.eventId(), NOME_CONSUMIDOR, agora);

        eventPublisher.publish(ResultadoRepublicado.novo(
            evento.concursoId(),
            contexto.cargoId(),
            evento.provaId(),
            evento.questaoId(),
            resultadosRecalculados.size(),
            novaClassificacao.size(),
            agora
        ));
    }
}
