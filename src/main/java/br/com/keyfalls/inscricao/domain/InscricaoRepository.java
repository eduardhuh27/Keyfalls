package br.com.keyfalls.inscricao.domain;

import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;

import java.util.List;
import java.util.Optional;

public interface InscricaoRepository {
    void salvar(Inscricao inscricao);
    Optional<Inscricao> buscarPorId(InscricaoId id);
    boolean existeInscricaoAtiva(CandidatoId candidatoId, ConcursoId concursoId);
    List<Inscricao> listarHomologadasPorConcurso(ConcursoId concursoId);
}
