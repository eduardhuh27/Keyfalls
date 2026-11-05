package br.com.keyfalls.recurso.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Portas de entrada (Inbound Ports) da camada application para o contexto Recurso.
 */
public interface RecursoUseCases {

    record AbrirRecursoCommand(
        UUID candidatoAutenticadoId,
        UUID concursoId,
        UUID provaId,
        UUID questaoId,
        String fundamentacao
    ) {}

    record RecursoProtocoladoOutput(
        UUID recursoId,
        UUID questaoId,
        Instant protocoladoEm
    ) {}

    record JulgarRecursoCommand(
        UUID recursoId,
        boolean deferido,
        boolean anularQuestao,
        String parecerBanca
    ) {}

    record RecursoJulgadoOutput(
        UUID recursoId,
        String decisao,
        boolean questaoAnulada,
        String parecerBanca,
        Instant julgadoEm
    ) {}

    record ResultadoFinalHomologadoOutput(
        UUID concursoId,
        int totalCargosHomologados,
        int totalClassificadosHomologados,
        Instant homologadoEm
    ) {}

    RecursoProtocoladoOutput abrirRecurso(AbrirRecursoCommand command);

    RecursoJulgadoOutput julgarRecurso(JulgarRecursoCommand command);

    ResultadoFinalHomologadoOutput homologarResultadoFinal(UUID concursoId);
}
