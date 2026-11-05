package br.com.keyfalls.apuracao.application;

import br.com.keyfalls.apuracao.domain.CalculadoraDeNota;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.ProvaId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta ACL no contexto Apuracao para obter matrizes atualizadas do Concurso e metadados de inscricao
 * sem acoplamento direto as entidades de outros Bounded Contexts.
 */
public interface ConsultaMatrizApuracaoPort {

    record ContextoRecalculoCargo(
        CargoId cargoId,
        PoliticaAnulacaoQuestao politicaAnulacao,
        List<CalculadoraDeNota.MatrizGabaritoProva> matrizesAtualizadasDoCargo
    ) {}

    record MetadadosCandidatoInscricao(
        InscricaoId inscricaoId,
        UUID candidatoId,
        String numeroInscricao,
        String nomeCandidato,
        CargoId cargoId
    ) {}

    ContextoRecalculoCargo obterContextoAtualizadoPorProva(ConcursoId concursoId, ProvaId provaId);

    List<ContextoRecalculoCargo> listarContextosDoConcurso(ConcursoId concursoId);

    Map<InscricaoId, MetadadosCandidatoInscricao> buscarMetadadosInscricoesDoConcurso(ConcursoId concursoId);

    Optional<MetadadosCandidatoInscricao> buscarInscricaoDoCandidatoNoConcurso(UUID candidatoId, ConcursoId concursoId);

    Map<ProvaId, String> mapearDisciplinaPorProvaDoConcurso(ConcursoId concursoId);

    ApuracaoUseCases.GabaritoVigenciaOutput consultarGabaritoVigenteDaProva(ProvaId provaId);
}
