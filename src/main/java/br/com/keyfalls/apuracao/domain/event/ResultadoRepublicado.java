package br.com.keyfalls.apuracao.domain.event;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ResultadoRepublicado(
    UUID eventId,
    Instant occurredOn,
    ConcursoId concursoId,
    CargoId cargoId,
    ProvaId provaAfetadaId,
    QuestaoId questaoAnuladaId,
    int totalResultadosRecalculados,
    int totalClassificadosAtualizado
) implements DomainEvent {

    public ResultadoRepublicado {
        Objects.requireNonNull(eventId, "eventId obrigatorio.");
        Objects.requireNonNull(occurredOn, "occurredOn obrigatorio.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio.");
        Objects.requireNonNull(provaAfetadaId, "provaAfetadaId obrigatorio.");
        Objects.requireNonNull(questaoAnuladaId, "questaoAnuladaId obrigatorio.");
        if (totalResultadosRecalculados < 0 || totalClassificadosAtualizado < 0
            || totalClassificadosAtualizado > totalResultadosRecalculados) {
            throw new RegraDeNegocioException("Contagens de republicacao invalidas no evento ResultadoRepublicado.");
        }
    }

    public static ResultadoRepublicado novo(
        ConcursoId concursoId,
        CargoId cargoId,
        ProvaId provaAfetadaId,
        QuestaoId questaoAnuladaId,
        int totalResultadosRecalculados,
        int totalClassificadosAtualizado,
        Instant occurredOn
    ) {
        return new ResultadoRepublicado(
            UUID.randomUUID(),
            occurredOn,
            concursoId,
            cargoId,
            provaAfetadaId,
            questaoAnuladaId,
            totalResultadosRecalculados,
            totalClassificadosAtualizado
        );
    }

    @Override
    public UUID aggregateId() {
        return concursoId.valor();
    }
}
