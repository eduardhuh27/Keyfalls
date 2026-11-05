package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.util.Objects;

public record Fiscal(String nomeCompleto, CPF cpf) {

    public Fiscal {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            throw new RegraDeNegocioException("Nome completo do fiscal obrigatorio.");
        }
        Objects.requireNonNull(cpf, "CPF do fiscal obrigatorio.");
        nomeCompleto = nomeCompleto.trim();
    }
}
