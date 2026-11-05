package br.com.keyfalls.inscricao.interfaces.rest.dto;

import br.com.keyfalls.inscricao.application.InscricaoRealizadaOutput;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InscricaoResponse(
    UUID inscricaoId,
    String numeroInscricao,
    String status,
    String linhaDigitavelBoleto,
    BigDecimal valorTaxa,
    LocalDate vencimentoBoleto
) {
    public static InscricaoResponse from(InscricaoRealizadaOutput output) {
        return new InscricaoResponse(
            output.inscricaoId(),
            output.numeroInscricao(),
            output.status(),
            output.linhaDigitavelBoleto(),
            output.valorTaxa(),
            output.vencimentoBoleto()
        );
    }
}
