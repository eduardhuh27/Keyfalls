package br.com.keyfalls.inscricao.infrastructure.gateway;

import br.com.keyfalls.inscricao.application.GatewayBoletoPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Etapa 4 — Adapter Mock ativo apenas nos perfis 'dev' e 'test'.
 * Na entrada em producao, um @Component @Profile("prod") (ex.: BancoDoBrasilBoletoRestAdapter)
 * assume a injecao sem alterar uma unica linha de RealizarInscricaoUseCase.
 */
@Component
@Profile({"dev", "test"})
public class MockBoletoAdapter implements GatewayBoletoPort {

    @Override
    public BoletoRegistradoDto registrarBoleto(DadosRegistroBoleto dados) {
        String sufixoValor = dados.valorNominal().movePointRight(2).toPlainString();
        String linhaDigitavelSimulada = "00190.00009 01234.567890 %s.1 99990000%s"
            .formatted(dados.numeroInscricao().codigo().substring(0, 10), sufixoValor);

        return new BoletoRegistradoDto(
            "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
            linhaDigitavelSimulada,
            "https://sandbox.boletos.keyfalls.local/pdf/" + dados.numeroInscricao().codigo()
        );
    }
}
