package br.com.keyfalls.concurso.application;

import br.com.keyfalls.shared.domain.Alternativa;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portas de entrada (Inbound Ports) da camada application para o contexto Concurso.
 */
public interface ConcursoUseCases {

    record CriarConcursoCommand(
        String codigoEdital,
        String titulo,
        String abrangencia,
        Instant inicioInscricao,
        Instant fimInscricao,
        Instant inicioIsencao,
        Instant fimIsencao,
        String politicaAnulacao
    ) {}

    record ConcursoCriadoOutput(
        UUID concursoId,
        String codigoEdital,
        String titulo,
        String abrangencia
    ) {}

    record ProvaInicialInput(String disciplina, BigDecimal peso) {}

    record AdicionarCargoComProvasCommand(
        UUID concursoId,
        String nomeCargo,
        int vagas,
        BigDecimal taxaInscricao,
        List<ProvaInicialInput> provas
    ) {}

    record CargoComProvasOutput(
        UUID cargoId,
        String nomeCargo,
        List<UUID> provasCriadasIds
    ) {}

    record CadastrarQuestoesCommand(
        UUID provaId,
        List<ItemQuestaoInput> questoes
    ) {
        public record ItemQuestaoInput(
            int numeroOrdem,
            String enunciado,
            Map<Alternativa, String> opcoes
        ) {}
    }

    record QuestoesCadastradasOutput(UUID provaId, List<UUID> questoesIds) {}

    record PublicarGabaritoCommand(
        UUID provaId,
        boolean definitivo,
        Map<UUID, Alternativa> respostasOficiais
    ) {}

    record GabaritoPublicadoOutput(
        UUID provaId,
        boolean definitivo,
        int totalQuestoes,
        Instant publicadoEm
    ) {}

    ConcursoCriadoOutput criarConcurso(CriarConcursoCommand command);

    CargoComProvasOutput adicionarCargoEProvas(AdicionarCargoComProvasCommand command);

    QuestoesCadastradasOutput cadastrarQuestoes(CadastrarQuestoesCommand command);

    GabaritoPublicadoOutput publicarOuAtualizarGabarito(PublicarGabaritoCommand command);
}
