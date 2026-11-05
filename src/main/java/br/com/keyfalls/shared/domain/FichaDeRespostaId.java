package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record FichaDeRespostaId(UUID valor) {
    public FichaDeRespostaId {
        Objects.requireNonNull(valor, "FichaDeRespostaId nao pode ser nulo.");
    }

    public static FichaDeRespostaId novo() {
        return new FichaDeRespostaId(UUID.randomUUID());
    }
}
