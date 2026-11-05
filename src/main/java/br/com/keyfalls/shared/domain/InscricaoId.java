package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record InscricaoId(UUID valor) {
    public InscricaoId {
        Objects.requireNonNull(valor, "InscricaoId nao pode ser nulo.");
    }

    public static InscricaoId novo() {
        return new InscricaoId(UUID.randomUUID());
    }
}
