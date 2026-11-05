package br.com.keyfalls.inscricao.application;

import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.NumeroInscricao;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Etapa 4 — Porta de Saida (Outbound Port) para emissao e registro de boleto bancario.
 * Isola o caso de uso de protocolos especificos de bancos (FEBRABAN CNAB / API PIX-Boleto).
 */
public interface GatewayBoletoPort {

    record DadosRegistroBoleto(
        NumeroInscricao numeroInscricao,
        CPF cpfPagador,
        String nomePagador,
        BigDecimal valorNominal,
        LocalDate dataVencimento
    ) {}

    record BoletoRegistradoDto(
        String nossoNumeroBanco,
        String linhaDigitavel,
        String urlPdfBoleto
    ) {}

    BoletoRegistradoDto registrarBoleto(DadosRegistroBoleto dados);
}
