package br.com.keyfalls.shared.domain;

import java.util.Objects;
import java.util.UUID;

public record QuestaoId(UUID valor) {
    public QuestaoId {
        Objects.requireNonNull(valor, "QuestaoId nao pode ser nulo.");
    }

    public static QuestaoId novo() {
        return new QuestaoId(UUID.randomUUID());
    }
}
