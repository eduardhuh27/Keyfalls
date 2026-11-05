package br.com.keyfalls.apuracao.domain.event;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotasCalculadas(
    UUID eventId,
    Instant occurredOn,
    ConcursoId concursoId,
    CargoId cargoId,
    int totalCandidatosAvaliados,
    int totalClassificados
) implements DomainEvent {

    public NotasCalculadas {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio.");
        if (totalCandidatosAvaliados <= 0 || totalClassificados < 0 || totalClassificados > totalCandidatosAvaliados) {
            throw new RegraDeNegocioException("Totais de avaliacao/classificacao inconsistentes no evento NotasCalculadas.");
        }
    }

    public static NotasCalculadas novo(
        ConcursoId concursoId,
        CargoId cargoId,
        int totalCandidatosAvaliados,
        int totalClassificados,
        Instant occurredOn
    ) {
        return new NotasCalculadas(
            UUID.randomUUID(), occurredOn, concursoId, cargoId, totalCandidatosAvaliados, totalClassificados
        );
    }

    @Override
    public UUID aggregateId() {
        return concursoId.valor();
    }
}
