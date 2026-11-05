package br.com.keyfalls.apuracao.domain.event;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ResultadoFinalPublicado(
    UUID eventId,
    Instant occurredOn,
    ConcursoId concursoId,
    CargoId cargoId,
    int totalClassificadosHomologados
) implements DomainEvent {

    public ResultadoFinalPublicado {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio.");
        if (totalClassificadosHomologados < 0) {
            throw new RegraDeNegocioException("Total de classificados homologados nao pode ser negativo.");
        }
    }

    public static ResultadoFinalPublicado novo(
        ConcursoId concursoId,
        CargoId cargoId,
        int totalClassificadosHomologados,
        Instant occurredOn
    ) {
        return new ResultadoFinalPublicado(
            UUID.randomUUID(), occurredOn, concursoId, cargoId, totalClassificadosHomologados
        );
    }

    @Override
    public UUID aggregateId() {
        return concursoId.valor();
    }
}
