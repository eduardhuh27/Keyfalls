package br.com.keyfalls.shared.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Porta para controle de idempotencia tecnica (padrao Idempotent Consumer / Inbox).
 */
public interface EventoProcessadoRepository {

    boolean jaFoiProcessado(UUID eventId, String nomeConsumidor);

    void registrarProcessamento(UUID eventId, String nomeConsumidor, Instant processadoEm);
}
