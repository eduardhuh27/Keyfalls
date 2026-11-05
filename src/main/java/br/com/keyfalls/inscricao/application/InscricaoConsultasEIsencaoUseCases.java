package br.com.keyfalls.inscricao.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Portas de entrada adicionais (Inbound Ports) do contexto Inscricao para consulta
 * de boleto, solicitacao de isencao e consulta de parecer (protegidas contra IDOR).
 */
public interface InscricaoConsultasEIsencaoUseCases {

    record BoletoOutput(
        UUID inscricaoId,
        String numeroInscricao,
        String linhaDigitavel,
        BigDecimal valor,
        LocalDate vencimento,
        boolean pago
    ) {}

    record SolicitarIsencaoCommand(
        UUID inscricaoId,
        UUID solicitanteAutenticadoId,
        String justificativaLegal
    ) {}

    record IsencaoOutput(
        UUID isencaoId,
        UUID inscricaoId,
        String situacao,
        String justificativaLegal,
        String parecerAvaliacao,
        Instant solicitadoEm,
        Instant avaliadoEm
    ) {}

    BoletoOutput consultarBoleto(UUID inscricaoId, UUID solicitanteId, boolean isAdmin);

    IsencaoOutput solicitarIsencao(SolicitarIsencaoCommand command);

    IsencaoOutput consultarIsencao(UUID isencaoId, UUID solicitanteId, boolean isAdmin);
}
