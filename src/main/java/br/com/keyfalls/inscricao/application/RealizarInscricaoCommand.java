package br.com.keyfalls.inscricao.application;

import java.util.Objects;
import java.util.UUID;

public record RealizarInscricaoCommand(
    UUID candidatoId,
    UUID concursoId,
    UUID cargoId
) {
    public RealizarInscricaoCommand {
        Objects.requireNonNull(candidatoId, "candidatoId obrigatorio no comando.");
        Objects.requireNonNull(concursoId, "concursoId obrigatorio no comando.");
        Objects.requireNonNull(cargoId, "cargoId obrigatorio no comando.");
    }
}
