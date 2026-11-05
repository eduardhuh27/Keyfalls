package br.com.keyfalls.concurso.domain;

import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;

import java.util.Optional;

public interface ConcursoRepository {
    void salvar(Concurso concurso);
    Optional<Concurso> buscarPorId(ConcursoId id);
    Optional<Concurso> buscarPorProvaId(ProvaId provaId);
}
