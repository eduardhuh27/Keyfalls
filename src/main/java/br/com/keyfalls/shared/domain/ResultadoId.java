package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record ResultadoId(UUID valor) {
    public ResultadoId {
        Objects.requireNonNull(valor, "ResultadoId nao pode ser nulo.");
    }

    public static ResultadoId novo() {
        return new ResultadoId(UUID.randomUUID());
    }
}
