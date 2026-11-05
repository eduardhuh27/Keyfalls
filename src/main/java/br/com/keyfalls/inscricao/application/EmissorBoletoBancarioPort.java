package br.com.keyfalls.inscricao.application;

import br.com.keyfalls.shared.domain.NumeroInscricao;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface EmissorBoletoBancarioPort {
    String registrarCobrancaBancaria(NumeroInscricao numeroInscricao, BigDecimal valor, LocalDate vencimento);
}
