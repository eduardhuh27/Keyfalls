package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record SalaId(UUID valor) {
    public SalaId {
        Objects.requireNonNull(valor, "SalaId nao pode ser nulo.");
    }

    public static SalaId novo() {
        return new SalaId(UUID.randomUUID());
    }
}
