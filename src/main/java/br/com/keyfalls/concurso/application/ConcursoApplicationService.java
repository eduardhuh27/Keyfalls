package br.com.keyfalls.concurso.application;

import br.com.keyfalls.concurso.domain.Cargo;
import br.com.keyfalls.concurso.domain.Concurso;
import br.com.keyfalls.concurso.domain.Concurso.Abrangencia;
import br.com.keyfalls.concurso.domain.ConcursoRepository;
import br.com.keyfalls.concurso.domain.Prova;
import br.com.keyfalls.concurso.domain.Questao;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Periodo;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import br.com.keyfalls.shared.domain.exception.EntidadeNaoEncontradaException;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementacao pura de aplicacao para os casos de uso do contexto Concurso.
 * Segue estritamente o ciclo: carregar Agregado -> executar comportamento rico -> salvar -> pullDomainEvents().
 */
public class ConcursoApplicationService implements ConcursoUseCases {

    private final ConcursoRepository concursoRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ConcursoApplicationService(
        ConcursoRepository concursoRepository,
        DomainEventPublisher eventPublisher,
        Clock clock
    ) {
        this.concursoRepository = Objects.requireNonNull(concursoRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ConcursoCriadoOutput criarConcurso(CriarConcursoCommand command) {
        Objects.requireNonNull(command, "CriarConcursoCommand obrigatorio.");
        Abrangencia abrangencia = Abrangencia.valueOf(command.abrangencia().trim().toUpperCase(Locale.ROOT));
        Periodo periodoInscricao = new Periodo(command.inicioInscricao(), command.fimInscricao());
        Periodo periodoIsencao = new Periodo(command.inicioIsencao(), command.fimIsencao());
        PoliticaAnulacaoQuestao politica = resolverPoliticaAnulacao(command.politicaAnulacao());

        Concurso concurso = Concurso.abrirEdital(
            ConcursoId.novo(),
            command.codigoEdital(),
            command.titulo(),
            abrangencia,
            periodoInscricao,
            periodoIsencao,
            politica
        );

        concursoRepository.salvar(concurso);
        concurso.pullDomainEvents().forEach(eventPublisher::publish);

        return new ConcursoCriadoOutput(
            concurso.id().valor(),
            concurso.codigoEdital(),
            concurso.titulo(),
            concurso.abrangencia().name()
        );
    }

    @Override
    public CargoComProvasOutput adicionarCargoEProvas(AdicionarCargoComProvasCommand command) {
        Objects.requireNonNull(command, "AdicionarCargoComProvasCommand obrigatorio.");
        ConcursoId concursoId = new ConcursoId(command.concursoId());
        Concurso concurso = concursoRepository.buscarPorId(concursoId)
            .orElseThrow(() -> new EntidadeNaoEncontradaException("Concurso nao encontrado: " + command.concursoId()));

        Cargo cargo = concurso.adicionarCargo(
            CargoId.novo(),
            command.nomeCargo(),
            command.vagas(),
            command.taxaInscricao()
        );

        List<UUID> provasCriadas = new ArrayList<>();
        if (command.provas() != null) {
            for (ProvaInicialInput p : command.provas()) {
                Prova prova = concurso.configurarProva(
                    ProvaId.novo(),
                    cargo.id(),
                    p.disciplina(),
                    new Peso(p.peso())
                );
                provasCriadas.add(prova.id().valor());
            }
        }

        concursoRepository.salvar(concurso);
        concurso.pullDomainEvents().forEach(eventPublisher::publish);

        return new CargoComProvasOutput(cargo.id().valor(), cargo.nome(), List.copyOf(provasCriadas));
    }

    @Override
    public QuestoesCadastradasOutput cadastrarQuestoes(CadastrarQuestoesCommand command) {
        Objects.requireNonNull(command, "CadastrarQuestoesCommand obrigatorio.");
        ProvaId provaId = new ProvaId(command.provaId());
        Concurso concurso = concursoRepository.buscarPorProvaId(provaId)
            .orElseThrow(() -> new EntidadeNaoEncontradaException("Prova nao encontrada: " + command.provaId()));

        List<UUID> questoesIds = new ArrayList<>();
        for (CadastrarQuestoesCommand.ItemQuestaoInput item : command.questoes()) {
            Questao questao = concurso.incluirQuestaoNaProva(
                provaId,
                QuestaoId.novo(),
                item.numeroOrdem(),
                item.enunciado(),
                item.opcoes()
            );
            questoesIds.add(questao.id().valor());
        }

        concursoRepository.salvar(concurso);
        concurso.pullDomainEvents().forEach(eventPublisher::publish);

        return new QuestoesCadastradasOutput(provaId.valor(), List.copyOf(questoesIds));
    }

    @Override
    public GabaritoPublicadoOutput publicarOuAtualizarGabarito(PublicarGabaritoCommand command) {
        Objects.requireNonNull(command, "PublicarGabaritoCommand obrigatorio.");
        ProvaId provaId = new ProvaId(command.provaId());
        Concurso concurso = concursoRepository.buscarPorProvaId(provaId)
            .orElseThrow(() -> new EntidadeNaoEncontradaException("Prova nao encontrada: " + command.provaId()));

        Map<QuestaoId, Alternativa> respostasTipadas = new LinkedHashMap<>();
        command.respostasOficiais().forEach((uuid, alt) -> respostasTipadas.put(new QuestaoId(uuid), alt));

        Instant agora = Instant.now(clock);
        concurso.publicarGabaritoPreliminar(provaId, respostasTipadas, agora);
        if (command.definitivo()) {
            concurso.consolidarGabaritoDefinitivo(provaId, agora);
        }

        concursoRepository.salvar(concurso);
        concurso.pullDomainEvents().forEach(eventPublisher::publish);

        return new GabaritoPublicadoOutput(
            provaId.valor(),
            command.definitivo(),
            respostasTipadas.size(),
            agora
        );
    }

    private static PoliticaAnulacaoQuestao resolverPoliticaAnulacao(String codigo) {
        if (codigo == null) {
            return new PoliticaAnulacaoQuestao.PontoUniversalStrategy();
        }
        String normalizado = codigo.trim().toUpperCase(Locale.ROOT);
        if (normalizado.contains("REDISTRIBUICAO") || normalizado.contains("DESCONSIDERAR")) {
            return new PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy();
        }
        return new PoliticaAnulacaoQuestao.PontoUniversalStrategy();
    }
}
