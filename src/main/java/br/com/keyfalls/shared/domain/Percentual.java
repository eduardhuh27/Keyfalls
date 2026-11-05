package br.com.keyfalls.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record Percentual(BigDecimal valor) implements Comparable<Percentual> {

    private static final BigDecimal CEM = new BigDecimal("100.00");

    public static final Percentual ZERO = new Percentual(BigDecimal.ZERO);
    public static final Percentual CINQUENTA_POR_CENTO = new Percentual(new BigDecimal("50.00"));
    public static final Percentual SESSENTA_POR_CENTO = new Percentual(new BigDecimal("60.00"));
    public static final Percentual CEM_POR_CENTO = new Percentual(CEM);

    public Percentual {
        Objects.requireNonNull(valor, "Percentual obrigatorio.");
        BigDecimal ajustado = valor.setScale(2, RoundingMode.HALF_UP);
        if (ajustado.compareTo(BigDecimal.ZERO) < 0 || ajustado.compareTo(CEM) > 0) {
            throw new RegraDeNegocioException("Percentual deve estar entre 0.00 e 100.00.");
        }
        valor = ajustado;
    }

    public static Percentual calcular(int parte, int total) {
        if (total <= 0) {
            throw new RegraDeNegocioException("Total deve ser maior que zero para calculo de percentual.");
        }
        if (parte < 0 || parte > total) {
            throw new RegraDeNegocioException("Parte fora do intervalo valido do total.");
        }
        BigDecimal resultado = BigDecimal.valueOf(parte)
            .multiply(CEM)
            .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new Percentual(resultado);
    }

    public boolean atingiuMinimo(Percentual minimo) {
        Objects.requireNonNull(minimo, "Percentual minimo obrigatorio.");
        return this.valor.compareTo(minimo.valor) >= 0;
    }

    @Override
    public int compareTo(Percentual outro) {
        return this.valor.compareTo(outro.valor);
    }
}
