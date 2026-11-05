package br.com.keyfalls.apuracao.domain.event;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record FichasProcessadas(
    UUID eventId,
    Instant occurredOn,
    ConcursoId concursoId,
    CargoId cargoId,
    int quantidadeFichasCapturadas
) implements DomainEvent {

    public FichasProcessadas {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio.");
        if (quantidadeFichasCapturadas <= 0) {
            throw new RegraDeNegocioException("Quantidade de fichas processadas deve ser maior que zero.");
        }
    }

    public static FichasProcessadas novo(
        ConcursoId concursoId,
        CargoId cargoId,
        int quantidadeFichasCapturadas,
        Instant occurredOn
    ) {
        return new FichasProcessadas(UUID.randomUUID(), occurredOn, concursoId, cargoId, quantidadeFichasCapturadas);
    }

    @Override
    public UUID aggregateId() {
        return concursoId.valor();
    }
}
