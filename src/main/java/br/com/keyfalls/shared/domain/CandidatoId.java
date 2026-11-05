package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record CandidatoId(UUID valor) {
    public CandidatoId {
        Objects.requireNonNull(valor, "CandidatoId nao pode ser nulo.");
    }

    public static CandidatoId novo() {
        return new CandidatoId(UUID.randomUUID());
    }
}
