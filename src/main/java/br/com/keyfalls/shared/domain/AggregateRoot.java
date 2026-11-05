package br.com.keyfalls.shared.domain;

import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Classe base para Raizes de Agregado (Aggregate Roots), responsavel por acumular
 * Eventos de Dominio em memoria durante mutacoes de negocio para disparo seguro
 * somente apos a persistencia transacional na camada de aplicacao.
 */
public abstract class AggregateRoot<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    public abstract ID id();

    protected final void registerEvent(DomainEvent event) {
        Objects.requireNonNull(event, "Evento de dominio registrado nao pode ser nulo.");
        this.domainEvents.add(event);
    }

    /**
     * Drena e retorna uma copia imutavel dos eventos pendentes, limpando a fila interna
     * para evitar disparo duplicado na mesma instancia do agregado.
     */
    public final List<DomainEvent> pullDomainEvents() {
        if (domainEvents.isEmpty()) {
            return List.of();
        }
        List<DomainEvent> snapshot = List.copyOf(domainEvents);
        domainEvents.clear();
        return snapshot;
    }

    public final List<DomainEvent> domainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
