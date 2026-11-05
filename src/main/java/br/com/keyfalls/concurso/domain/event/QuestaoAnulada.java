package br.com.keyfalls.concurso.domain.event;

import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record QuestaoAnulada(
    UUID eventId,
    Instant occurredOn,
    ConcursoId concursoId,
    ProvaId provaId,
    QuestaoId questaoId,
    String justificativa
) implements DomainEvent {

    public QuestaoAnulada {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        Objects.requireNonNull(provaId, "provaId obrigatorio.");
        Objects.requireNonNull(questaoId, "questaoId obrigatorio.");
        if (justificativa == null || justificativa.isBlank()) {
            throw new RegraDeNegocioException("Justificativa da anulacao obrigatoria no evento QuestaoAnulada.");
        }
        justificativa = justificativa.trim();
    }

    public static QuestaoAnulada novo(
        ConcursoId concursoId,
        ProvaId provaId,
        QuestaoId questaoId,
        String justificativa,
        Instant occurredOn
    ) {
        return new QuestaoAnulada(UUID.randomUUID(), occurredOn, concursoId, provaId, questaoId, justificativa);
    }

    @Override
    public UUID aggregateId() {
        return concursoId.valor();
    }
}
