package br.com.keyfalls.inscricao.domain.event;

import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InscricaoRealizada(
    UUID eventId,
    Instant occurredOn,
    InscricaoId inscricaoId,
    NumeroInscricao numeroInscricao,
    CandidatoId candidatoId,
    ConcursoId concursoId,
    CargoId cargoId
) implements DomainEvent {

    public InscricaoRealizada {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(inscricaoId, "inscricaoId obrigatorio.");
        Objects.requireNonNull(numeroInscricao, "numeroInscricao obrigatorio.");
        Objects.requireNonNull(candidatoId, "candidatoId obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio.");
    }

    public static InscricaoRealizada novo(
        InscricaoId inscricaoId,
        NumeroInscricao numeroInscricao,
        CandidatoId candidatoId,
        ConcursoId concursoId,
        CargoId cargoId,
        Instant occurredOn
    ) {
        return new InscricaoRealizada(
            UUID.randomUUID(), occurredOn, inscricaoId, numeroInscricao, candidatoId, concursoId, cargoId
        );
    }

    @Override
    public UUID aggregateId() {
        return inscricaoId.valor();
    }
}
