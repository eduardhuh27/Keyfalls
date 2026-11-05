package br.com.keyfalls.shared.domain.event;

import java.util.Collection;
import java.util.Objects;

/**
 * Porta de saida (Outbound Port) para publicacao desacoplada de Eventos de Dominio.
 * Permite substituir o adapter em memoria (Spring ApplicationEventPublisher)
 * por RabbitMQ, Kafka ou Transactional Outbox sem tocar no dominio.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);

    default void publishAll(Collection<? extends DomainEvent> events) {
        Objects.requireNonNull(events, "Colecao de eventos nao pode ser nula.");
        events.forEach(this::publish);
    }
}
