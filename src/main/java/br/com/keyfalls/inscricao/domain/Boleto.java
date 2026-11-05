package br.com.keyfalls.inscricao.domain;

import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record Boleto(
    String linhaDigitavel,
    BigDecimal valor,
    LocalDate vencimento,
    boolean pago,
    Instant liquidadoEm
) {

    public Boleto {
        if (linhaDigitavel == null || linhaDigitavel.isBlank()) {
            throw new RegraDeNegocioException("Linha digitavel do boleto obrigatoria.");
        }
        Objects.requireNonNull(valor, "Valor do boleto obrigatorio.");
        if (valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraDeNegocioException("Valor do boleto deve ser maior que zero.");
        }
        Objects.requireNonNull(vencimento, "Data de vencimento do boleto obrigatoria.");
        if (pago && liquidadoEm == null) {
            throw new RegraDeNegocioException("Data de liquidacao obrigatoria para boleto pago.");
        }
        linhaDigitavel = linhaDigitavel.trim();
        valor = valor.setScale(2, RoundingMode.HALF_UP);
    }

    public static Boleto emitir(String linhaDigitavel, BigDecimal valor, LocalDate vencimento) {
        return new Boleto(linhaDigitavel, valor, vencimento, false, null);
    }

    public Boleto liquidar(Instant dataPagamento) {
        if (this.pago) {
            throw new RegraDeNegocioException("Este boleto ja foi liquidado anteriormente.");
        }
        return new Boleto(this.linhaDigitavel, this.valor, this.vencimento, true, dataPagamento);
    }
}
