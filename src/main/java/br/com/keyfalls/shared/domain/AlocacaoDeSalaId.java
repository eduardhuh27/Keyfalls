package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record AlocacaoDeSalaId(UUID valor) {
    public AlocacaoDeSalaId {
        Objects.requireNonNull(valor, "AlocacaoDeSalaId nao pode ser nulo.");
    }

    public static AlocacaoDeSalaId novo() {
        return new AlocacaoDeSalaId(UUID.randomUUID());
    }
}
