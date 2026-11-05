package br.com.keyfalls.recurso.domain;

import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RecursoId;

import java.util.List;
import java.util.Optional;

public interface RecursoRepository {
    void salvar(Recurso recurso);
    Optional<Recurso> buscarPorId(RecursoId id);
    List<Recurso> listarPorQuestao(QuestaoId questaoId);
    List<Recurso> listarPorConcurso(ConcursoId concursoId);
    long contarPendentesPorConcurso(ConcursoId concursoId);
}
