package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record RecursoId(UUID valor) {
    public RecursoId {
        Objects.requireNonNull(valor, "RecursoId nao pode ser nulo.");
    }

    public static RecursoId novo() {
        return new RecursoId(UUID.randomUUID());
    }
}
