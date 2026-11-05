package br.com.keyfalls.recurso.application;

import br.com.keyfalls.apuracao.domain.event.ResultadoFinalPublicado;
import br.com.keyfalls.recurso.domain.Recurso;
import br.com.keyfalls.recurso.domain.RecursoRepository;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RecursoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import br.com.keyfalls.shared.domain.exception.RecursosPendentesException;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementacao concreta dos Use Cases do Bounded Context Recurso.
 * Carrega o agregado Recurso, executa metodos ricos de dominio, persiste no RecursoRepository
 * e publica os eventos drenados por pullDomainEvents() (ex.: QuestaoAnulada e ResultadoFinalPublicado).
 */
public class RecursoApplicationService implements RecursoUseCases {

    private final RecursoRepository recursoRepository;
    private final HomologacaoConcursoPort homologacaoConcursoPort;
    private final DomainEventPublisher eventPublisher;

    public RecursoApplicationService(
        RecursoRepository recursoRepository,
        HomologacaoConcursoPort homologacaoConcursoPort,
        DomainEventPublisher eventPublisher
    ) {
        this.recursoRepository = Objects.requireNonNull(recursoRepository);
        this.homologacaoConcursoPort = Objects.requireNonNull(homologacaoConcursoPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public RecursoProtocoladoOutput abrirRecurso(AbrirRecursoCommand command) {
        Objects.requireNonNull(command, "Comando de abertura de recurso obrigatorio.");
        Instant protocoladoEm = Instant.now();

        Recurso recurso = Recurso.interpor(
            RecursoId.novo(),
            new ConcursoId(command.concursoId()),
            new ProvaId(command.provaId()),
            new QuestaoId(command.questaoId()),
            new CandidatoId(command.candidatoAutenticadoId()),
            command.fundamentacao(),
            protocoladoEm
        );

        recursoRepository.salvar(recurso);
        recurso.pullDomainEvents().forEach(eventPublisher::publish);

        return new RecursoProtocoladoOutput(
            recurso.id().valor(),
            recurso.questaoId().valor(),
            recurso.protocoladoEm()
        );
    }

    @Override
    public RecursoJulgadoOutput julgarRecurso(JulgarRecursoCommand command) {
        Objects.requireNonNull(command, "Comando de julgamento de recurso obrigatorio.");
        Recurso recurso = recursoRepository.buscarPorId(new RecursoId(command.recursoId()))
            .orElseThrow(() -> new RegraDeNegocioException("Recurso nao encontrado: " + command.recursoId()));

        Instant julgadoEm = Instant.now();
        boolean questaoAnulada = false;

        if (command.deferido()) {
            if (command.anularQuestao()) {
                recurso.deferirComAnulacaoDeQuestao(command.parecerBanca(), julgadoEm);
                homologacaoConcursoPort.marcarQuestaoAnuladaNaMatriz(recurso.provaId(), recurso.questaoId());
                questaoAnulada = true;
            } else {
                recurso.deferir(command.parecerBanca(), julgadoEm);
            }
        } else {
            recurso.indeferir(command.parecerBanca(), julgadoEm);
        }

        recursoRepository.salvar(recurso);
        recurso.pullDomainEvents().forEach(eventPublisher::publish);

        return new RecursoJulgadoOutput(
            recurso.id().valor(),
            recurso.decisao().name(),
            questaoAnulada,
            recurso.parecerBanca(),
            recurso.julgadoEm()
        );
    }

    @Override
    public ResultadoFinalHomologadoOutput homologarResultadoFinal(UUID concursoId) {
        Objects.requireNonNull(concursoId, "ConcursoId obrigatorio para homologacao do resultado final.");
        ConcursoId idConcurso = new ConcursoId(concursoId);

        long pendentes = recursoRepository.contarPendentesPorConcurso(idConcurso);
        if (pendentes > 0) {
            throw new RecursosPendentesException(
                "Bloqueio de homologacao: existem " + pendentes
                    + " recursos aguardando julgamento da banca para o concurso " + concursoId + "."
            );
        }

        Instant homologadoEm = Instant.now();
        Map<CargoId, Integer> classificadosPorCargo =
            homologacaoConcursoPort.encerrarConcursoEContarClassificadosPorCargo(idConcurso, homologadoEm);

        int totalClassificados = 0;
        for (Map.Entry<CargoId, Integer> entry : classificadosPorCargo.entrySet()) {
            int qtdCargo = entry.getValue();
            totalClassificados += qtdCargo;
            eventPublisher.publish(ResultadoFinalPublicado.novo(
                idConcurso,
                entry.getKey(),
                qtdCargo,
                homologadoEm
            ));
        }

        return new ResultadoFinalHomologadoOutput(
            concursoId,
            classificadosPorCargo.size(),
            totalClassificados,
            homologadoEm
        );
    }
}
