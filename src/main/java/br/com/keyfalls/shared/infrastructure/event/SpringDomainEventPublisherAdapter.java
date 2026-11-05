package br.com.keyfalls.shared.infrastructure.event;

import br.com.keyfalls.shared.domain.event.DomainEvent;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Adapter de infraestrutura inicial usando Spring ApplicationEventPublisher em memoria.
 * Para migracao futura a microsservicos (RabbitMQ / Kafka), basta prover uma nova
 * implementacao de DomainEventPublisher (ou Transactional Outbox Relay) sem alterar
 * nenhuma linha das camadas domain ou application.
 */
@Component
public class SpringDomainEventPublisherAdapter implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringDomainEventPublisherAdapter(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = Objects.requireNonNull(applicationEventPublisher);
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "DomainEvent nao pode ser nulo ao publicar.");
        applicationEventPublisher.publishEvent(event);
    }
}
