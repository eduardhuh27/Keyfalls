package br.com.keyfalls.logistica.domain.event;

import br.com.keyfalls.shared.domain.AlocacaoDeSalaId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.SalaId;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SalaAlocada(
    UUID eventId,
    Instant occurredOn,
    AlocacaoDeSalaId alocacaoId,
    InscricaoId inscricaoId,
    CargoId cargoId,
    LocalDeProvaId localDeProvaId,
    SalaId salaId
) implements DomainEvent {

    public SalaAlocada {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(alocacaoId, "alocacaoId obrigatorio.");
        Objects.requireNonNull(inscricaoId, "inscricaoId obrigatorio.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio.");
        Objects.requireNonNull(localDeProvaId, "localDeProvaId obrigatorio.");
        Objects.requireNonNull(salaId, "salaId obrigatorio.");
    }

    public static SalaAlocada novo(
        AlocacaoDeSalaId alocacaoId,
        InscricaoId inscricaoId,
        CargoId cargoId,
        LocalDeProvaId localDeProvaId,
        SalaId salaId,
        Instant occurredOn
    ) {
        return new SalaAlocada(
            UUID.randomUUID(), occurredOn, alocacaoId, inscricaoId, cargoId, localDeProvaId, salaId
        );
    }

    @Override
    public UUID aggregateId() {
        return alocacaoId.valor();
    }
}
