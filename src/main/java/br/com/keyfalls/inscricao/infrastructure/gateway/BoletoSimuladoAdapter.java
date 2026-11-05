package br.com.keyfalls.inscricao.infrastructure.gateway;

import br.com.keyfalls.inscricao.application.EmissorBoletoBancarioPort;
import br.com.keyfalls.inscricao.application.GatewayBoletoPort;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Adapter deterministico de emissao de boleto bancario (padrao FEBRABAN 47 posicoes),
 * implementando GatewayBoletoPort e EmissorBoletoBancarioPort para GET /inscricoes/{id}/boleto.
 */
@Primary
@Component
public class BoletoSimuladoAdapter implements GatewayBoletoPort, EmissorBoletoBancarioPort {

    private static final LocalDate DATA_BASE_FEBRABAN = LocalDate.of(1997, 10, 7);

    @Override
    public String registrarCobrancaBancaria(NumeroInscricao numeroInscricao, BigDecimal valor, LocalDate vencimento) {
        String codigoNumerico = numeroInscricao.codigo().replaceAll("\\D", "");
        String dezDigitos = (codigoNumerico + "0000000000").substring(0, 10);
        long fatorVencimento = Math.max(1000L, ChronoUnit.DAYS.between(DATA_BASE_FEBRABAN, vencimento));
        String valorCentavos = "%010d".formatted(
            valor.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        );

        return "00190.00009 02026.%s9 %s.17 8 %04d%s".formatted(
            dezDigitos.substring(0, 5),
            dezDigitos,
            fatorVencimento % 10000,
            valorCentavos
        );
    }

    @Override
    public BoletoRegistradoDto registrarBoleto(DadosRegistroBoleto dados) {
        String linhaDigitavel = registrarCobrancaBancaria(
            dados.numeroInscricao(),
            dados.valorNominal(),
            dados.dataVencimento()
        );
        return new BoletoRegistradoDto(
            "BB-" + dados.numeroInscricao().codigo(),
            linhaDigitavel,
            "https://boletos.keyfalls.com.br/" + dados.numeroInscricao().codigo() + ".pdf"
        );
    }
}
