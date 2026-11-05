package br.com.keyfalls.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Contrato base agnostico de frameworks para todos os Eventos de Dominio da API KeyFalls.
 */
public interface DomainEvent {

    UUID eventId();

    Instant occurredOn();

    UUID aggregateId();
}
