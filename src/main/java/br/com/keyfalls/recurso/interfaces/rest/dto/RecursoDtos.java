package br.com.keyfalls.recurso.interfaces.rest.dto;

import br.com.keyfalls.recurso.application.RecursoUseCases;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class RecursoDtos {

    private RecursoDtos() {}

    public record AbrirRecursoRequest(
        @NotNull(message = "O identificador do concurso e obrigatorio.")
        UUID concursoId,

        @NotNull(message = "O identificador da prova e obrigatorio.")
        UUID provaId,

        @NotNull(message = "O identificador da questao contestada e obrigatorio.")
        UUID questaoId,

        @NotBlank(message = "A fundamentacao argumentativa e obrigatoria.")
        @Size(min = 15, max = 4000, message = "A fundamentacao deve conter entre 15 e 4000 caracteres.")
        String fundamentacao
    ) {
        public RecursoUseCases.AbrirRecursoCommand toCommand(UUID candidatoAutenticadoId) {
            return new RecursoUseCases.AbrirRecursoCommand(
                candidatoAutenticadoId, concursoId, provaId, questaoId, fundamentacao
            );
        }
    }

    public record RecursoProtocoladoResponse(UUID recursoId, UUID questaoId, Instant protocoladoEm) {
        public static RecursoProtocoladoResponse from(RecursoUseCases.RecursoProtocoladoOutput out) {
            return new RecursoProtocoladoResponse(out.recursoId(), out.questaoId(), out.protocoladoEm());
        }
    }

    public record JulgarRecursoRequest(
        boolean deferido,
        boolean anularQuestao,

        @NotBlank(message = "O parecer oficial da banca examinadora e obrigatorio.")
        @Size(min = 10, max = 4000, message = "O parecer deve conter entre 10 e 4000 caracteres.")
        String parecerBanca
    ) {
        public RecursoUseCases.JulgarRecursoCommand toCommand(UUID recursoId) {
            return new RecursoUseCases.JulgarRecursoCommand(recursoId, deferido, anularQuestao, parecerBanca);
        }
    }

    public record RecursoJulgadoResponse(
        UUID recursoId,
        String decisao,
        boolean questaoAnulada,
        String parecerBanca,
        Instant julgadoEm
    ) {
        public static RecursoJulgadoResponse from(RecursoUseCases.RecursoJulgadoOutput out) {
            return new RecursoJulgadoResponse(
                out.recursoId(), out.decisao(), out.questaoAnulada(), out.parecerBanca(), out.julgadoEm()
            );
        }
    }

    public record ResultadoFinalResponse(
        UUID concursoId,
        int totalCargosHomologados,
        int totalClassificadosHomologados,
        Instant homologadoEm
    ) {
        public static ResultadoFinalResponse from(RecursoUseCases.ResultadoFinalHomologadoOutput out) {
            return new ResultadoFinalResponse(
                out.concursoId(), out.totalCargosHomologados(),
                out.totalClassificadosHomologados(), out.homologadoEm()
            );
        }
    }
}
