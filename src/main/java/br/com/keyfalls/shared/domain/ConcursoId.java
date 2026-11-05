package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record ConcursoId(UUID valor) {
    public ConcursoId {
        Objects.requireNonNull(valor, "ConcursoId nao pode ser nulo.");
    }

    public static ConcursoId novo() {
        return new ConcursoId(UUID.randomUUID());
    }
}
