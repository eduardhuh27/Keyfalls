package br.com.keyfalls.inscricao.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InscricaoRealizadaOutput(
    UUID inscricaoId,
    String numeroInscricao,
    String status,
    String linhaDigitavelBoleto,
    BigDecimal valorTaxa,
    LocalDate vencimentoBoleto
) {}
