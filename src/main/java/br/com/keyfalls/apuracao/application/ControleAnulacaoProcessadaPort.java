package br.com.keyfalls.apuracao.application;

import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta de idempotencia semantica de negocio: evita que a mesma questao anulada
 * dispare multiplos lotes de recalculo caso mais de um recurso seja deferido sobre ela.
 */
public interface ControleAnulacaoProcessadaPort {

    boolean anulacaoJaAplicada(ProvaId provaId, QuestaoId questaoId);

    void registrarAnulacaoAplicada(ProvaId provaId, QuestaoId questaoId, UUID eventIdOrigem, Instant aplicadoEm);
}
