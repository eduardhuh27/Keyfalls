package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record LocalDeProvaId(UUID valor) {
    public LocalDeProvaId {
        Objects.requireNonNull(valor, "LocalDeProvaId nao pode ser nulo.");
    }

    public static LocalDeProvaId novo() {
        return new LocalDeProvaId(UUID.randomUUID());
    }
}
