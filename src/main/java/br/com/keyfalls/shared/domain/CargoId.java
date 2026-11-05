package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record CargoId(UUID valor) {
    public CargoId {
        Objects.requireNonNull(valor, "CargoId nao pode ser nulo.");
    }

    public static CargoId novo() {
        return new CargoId(UUID.randomUUID());
    }
}
