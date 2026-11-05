package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;

import java.util.List;
import java.util.Optional;

public interface LocalDeProvaRepository {
    void salvar(LocalDeProva localDeProva);
    Optional<LocalDeProva> buscarPorId(LocalDeProvaId id);
    List<LocalDeProva> listarPorConcurso(ConcursoId concursoId);
}
