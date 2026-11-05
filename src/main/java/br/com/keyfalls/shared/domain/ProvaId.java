package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record ProvaId(UUID valor) {
    public ProvaId {
        Objects.requireNonNull(valor, "ProvaId nao pode ser nulo.");
    }

    public static ProvaId novo() {
        return new ProvaId(UUID.randomUUID());
    }
}
