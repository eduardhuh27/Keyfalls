package br.com.keyfalls.inscricao.interfaces.rest.dto;

import br.com.keyfalls.inscricao.application.InscricaoConsultasEIsencaoUseCases;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class InscricaoIsencaoDtos {

    private InscricaoIsencaoDtos() {}

    public record SolicitarIsencaoRequest(
        @NotBlank(message = "A justificativa legal para isencao de taxa e obrigatoria.")
        @Size(min = 10, max = 2000, message = "A justificativa deve conter entre 10 e 2000 caracteres.")
        String justificativaLegal
    ) {}

    public record BoletoResponse(
        UUID inscricaoId,
        String numeroInscricao,
        String linhaDigitavel,
        BigDecimal valor,
        LocalDate vencimento,
        boolean pago
    ) {
        public static BoletoResponse from(InscricaoConsultasEIsencaoUseCases.BoletoOutput out) {
            return new BoletoResponse(
                out.inscricaoId(), out.numeroInscricao(), out.linhaDigitavel(),
                out.valor(), out.vencimento(), out.pago()
            );
        }
    }

    public record IsencaoResponse(
        UUID isencaoId,
        UUID inscricaoId,
        String situacao,
        String justificativaLegal,
        String parecerAvaliacao,
        Instant solicitadoEm,
        Instant avaliadoEm
    ) {
        public static IsencaoResponse from(InscricaoConsultasEIsencaoUseCases.IsencaoOutput out) {
            return new IsencaoResponse(
                out.isencaoId(), out.inscricaoId(), out.situacao(),
                out.justificativaLegal(), out.parecerAvaliacao(),
                out.solicitadoEm(), out.avaliadoEm()
            );
        }
    }
}
