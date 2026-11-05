package br.com.keyfalls.concurso.domain.event;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GabaritoPublicado(
    UUID eventId,
    Instant occurredOn,
    ConcursoId concursoId,
    CargoId cargoId,
    ProvaId provaId,
    boolean definitivo
) implements DomainEvent {

    public GabaritoPublicado {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio.");
        Objects.requireNonNull(provaId, "provaId obrigatorio.");
    }

    public static GabaritoPublicado novo(
        ConcursoId concursoId,
        CargoId cargoId,
        ProvaId provaId,
        boolean definitivo,
        Instant occurredOn
    ) {
        return new GabaritoPublicado(UUID.randomUUID(), occurredOn, concursoId, cargoId, provaId, definitivo);
    }

    @Override
    public UUID aggregateId() {
        return concursoId.valor();
    }
}
