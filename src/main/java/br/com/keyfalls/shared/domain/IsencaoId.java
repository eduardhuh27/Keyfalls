package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record IsencaoId(UUID valor) {
    public IsencaoId {
        Objects.requireNonNull(valor, "IsencaoId nao pode ser nulo.");
    }

    public static IsencaoId novo() {
        return new IsencaoId(UUID.randomUUID());
    }
}
