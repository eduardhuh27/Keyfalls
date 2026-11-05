package br.com.keyfalls.inscricao.domain.event;

import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IsencaoJulgada(
    UUID eventId,
    Instant occurredOn,
    InscricaoId inscricaoId,
    NumeroInscricao numeroInscricao,
    CandidatoId candidatoId,
    ConcursoId concursoId,
    boolean deferida,
    String motivoDecisao
) implements DomainEvent {

    public IsencaoJulgada {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(inscricaoId, "inscricaoId obrigatorio.");
        Objects.requireNonNull(numeroInscricao, "numeroInscricao obrigatorio.");
        Objects.requireNonNull(candidatoId, "candidatoId obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        if (motivoDecisao == null || motivoDecisao.isBlank()) {
            throw new RegraDeNegocioException("Motivo/parecer da decisao de isencao obrigatorio no evento.");
        }
        motivoDecisao = motivoDecisao.trim();
    }

    public static IsencaoJulgada novo(
        InscricaoId inscricaoId,
        NumeroInscricao numeroInscricao,
        CandidatoId candidatoId,
        ConcursoId concursoId,
        boolean deferida,
        String motivoDecisao,
        Instant occurredOn
    ) {
        return new IsencaoJulgada(
            UUID.randomUUID(), occurredOn, inscricaoId, numeroInscricao, candidatoId, concursoId, deferida, motivoDecisao
        );
    }

    @Override
    public UUID aggregateId() {
        return inscricaoId.valor();
    }
}
