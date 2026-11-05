package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.Percentual;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record NotaProva(
    ProvaId provaId,
    Peso peso,
    int acertos,
    int erros,
    int totalQuestoesConsideradas,
    Percentual percentualAcertos,
    BigDecimal notaNaEscalaCem
) {

    public NotaProva {
        Objects.requireNonNull(provaId, "ProvaId obrigatorio na nota.");
        Objects.requireNonNull(peso, "Peso da prova obrigatorio na nota.");
        if (totalQuestoesConsideradas <= 0) {
            throw new RegraDeNegocioException("Total de questoes consideradas na prova deve ser maior que zero.");
        }
        if (acertos < 0 || erros < 0 || (acertos + erros) > totalQuestoesConsideradas) {
            throw new RegraDeNegocioException("Contagem de acertos e erros inconsistente com o total de questoes consideradas.");
        }
        Objects.requireNonNull(percentualAcertos, "Percentual de acertos obrigatorio.");
        Objects.requireNonNull(notaNaEscalaCem, "Nota da prova obrigatoria.");
        notaNaEscalaCem = notaNaEscalaCem.setScale(2, RoundingMode.HALF_UP);
    }

    public static NotaProva criar(
        ProvaId provaId,
        Peso peso,
        int acertos,
        int erros,
        int totalQuestoesConsideradas
    ) {
        Percentual percentual = Percentual.calcular(acertos, totalQuestoesConsideradas);
        return new NotaProva(
            provaId,
            peso,
            acertos,
            erros,
            totalQuestoesConsideradas,
            percentual,
            percentual.valor()
        );
    }

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int ESCALA_INTERMEDIARIA_PONDERACAO = 10;

    public boolean atingiuMinimoIndividual() {
        return percentualAcertos.atingiuMinimo(Percentual.CINQUENTA_POR_CENTO);
    }

    /**
     * Preserva 10 casas decimais para calculo intermediario da media ponderada em Resultado,
     * evitando tanto ArithmeticException em dizimas periodicas (ex.: 7 ou 19 questoes)
     * quanto perda de centesimos por arredondamento prematuro antes da multiplicacao pelo Peso.
     */
    public BigDecimal notaExataParaPonderacao() {
        return BigDecimal.valueOf(acertos)
            .multiply(CEM)
            .divide(BigDecimal.valueOf(totalQuestoesConsideradas), ESCALA_INTERMEDIARIA_PONDERACAO, RoundingMode.HALF_UP);
    }
}
