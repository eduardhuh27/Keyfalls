package br.com.keyfalls.concurso.domain;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public final class Cargo {

    private final CargoId id;
    private final String nome;
    private final int vagas;
    private final BigDecimal taxaInscricao;

    Cargo(CargoId id, String nome, int vagas, BigDecimal taxaInscricao) {
        this.id = Objects.requireNonNull(id, "CargoId obrigatorio.");
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Nome do cargo obrigatorio.");
        }
        if (vagas <= 0) {
            throw new RegraDeNegocioException("Quantidade de vagas do cargo deve ser maior que zero.");
        }
        Objects.requireNonNull(taxaInscricao, "Taxa de inscricao obrigatoria.");
        if (taxaInscricao.compareTo(BigDecimal.ZERO) < 0) {
            throw new RegraDeNegocioException("Taxa de inscricao nao pode ser negativa.");
        }
        this.nome = nome.trim();
        this.vagas = vagas;
        this.taxaInscricao = taxaInscricao.setScale(2, RoundingMode.HALF_UP);
    }

    public CargoId id() {
        return id;
    }

    public String nome() {
        return nome;
    }

    public int vagas() {
        return vagas;
    }

    public BigDecimal taxaInscricao() {
        return taxaInscricao;
    }
}
