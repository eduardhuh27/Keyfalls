package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.FichaDeRespostaId;
import br.com.keyfalls.shared.domain.InscricaoId;

import java.util.List;
import java.util.Optional;

public interface FichaDeRespostaRepository {
    void salvar(FichaDeResposta fichaDeResposta);
    Optional<FichaDeResposta> buscarPorId(FichaDeRespostaId id);
    Optional<FichaDeResposta> buscarPorInscricao(InscricaoId inscricaoId);
    List<FichaDeResposta> listarPorConcurso(ConcursoId concursoId);
}
