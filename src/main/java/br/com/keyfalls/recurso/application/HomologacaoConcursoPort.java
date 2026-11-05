package br.com.keyfalls.recurso.application;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;

import java.time.Instant;
import java.util.Map;

/**
 * Secondary Output Port (ACL) do contexto Recurso para sincronizar a anulacao de questao
 * na matriz e o bloqueio de encerramento/homologacao final do concurso.
 */
public interface HomologacaoConcursoPort {

    void marcarQuestaoAnuladaNaMatriz(ProvaId provaId, QuestaoId questaoId);

    Map<CargoId, Integer> encerrarConcursoEContarClassificadosPorCargo(ConcursoId concursoId, Instant homologadoEm);
}
