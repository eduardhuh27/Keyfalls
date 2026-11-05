package br.com.keyfalls.logistica.interfaces.rest.dto;

import br.com.keyfalls.logistica.application.LogisticaUseCases;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LogisticaDtos {

    private LogisticaDtos() {}

    public record SalaInputRequest(
        @NotBlank(message = "A identificacao da sala e obrigatoria.")
        String identificacao,

        @Positive(message = "A capacidade maxima da sala deve ser maior que zero.")
        int capacidadeMaxima,

        @NotNull(message = "O cargo atendido pela sala e obrigatorio.")
        UUID cargoAtendidoId,

        @NotBlank(message = "O nome do fiscal responsavel e obrigatorio.")
        String nomeFiscal,

        @NotBlank(message = "O CPF do fiscal e obrigatorio.")
        String cpfFiscal
    ) {}

    public record CadastrarLocalProvaRequest(
        @NotBlank(message = "O nome da instituicao e obrigatorio.")
        String nomeInstituicao,

        @NotBlank String logradouro,
        @NotBlank String numero,
        @NotBlank String bairro,
        @NotBlank String cidade,

        @NotBlank
        @Size(min = 2, max = 2, message = "A UF deve possuir 2 letras.")
        String uf,

        @NotBlank
        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "CEP invalido.")
        String cep,

        Double latitude,
        Double longitude,

        @NotEmpty(message = "Cadastre ao menos uma sala no local de prova.")
        @Valid
        List<SalaInputRequest> salas
    ) {
        public LogisticaUseCases.CadastrarLocalDeProvaCommand toCommand(UUID concursoId) {
            var salasCmd = salas.stream()
                .map(s -> new LogisticaUseCases.SalaCadastroInput(
                    s.identificacao(), s.capacidadeMaxima(), s.cargoAtendidoId(), s.nomeFiscal(), s.cpfFiscal()
                ))
                .toList();
            return new LogisticaUseCases.CadastrarLocalDeProvaCommand(
                concursoId, nomeInstituicao, logradouro, numero, bairro, cidade, uf, cep, latitude, longitude, salasCmd
            );
        }
    }

    public record LocalProvaResponse(UUID localDeProvaId, String nomeInstituicao, List<UUID> salasIds) {
        public static LocalProvaResponse from(LogisticaUseCases.LocalDeProvaCriadoOutput out) {
            return new LocalProvaResponse(out.localDeProvaId(), out.nomeInstituicao(), out.salasIds());
        }
    }

    public record AlocacaoExecutadaResponse(UUID concursoId, int totalCandidatosAlocados, Instant processadoEm) {
        public static AlocacaoExecutadaResponse from(LogisticaUseCases.AlocacaoEmLoteOutput out) {
            return new AlocacaoExecutadaResponse(out.concursoId(), out.totalCandidatosAlocados(), out.processadoEm());
        }
    }

    public record ItemPresencaRequest(
        @NotNull(message = "O identificador da inscricao e obrigatorio.")
        UUID inscricaoId,

        boolean presente,
        String hashAssinaturaCandidato,
        String documentoApresentado
    ) {}

    public record RegistrarPresencasSalaRequest(
        @NotEmpty(message = "A lista de presenca nao pode estar vazia.")
        @Valid
        List<ItemPresencaRequest> registros
    ) {
        public LogisticaUseCases.RegistrarListaPresencaCommand toCommand(UUID salaId, UUID operadorId) {
            var itens = registros.stream()
                .map(r -> new LogisticaUseCases.RegistroPresencaItemInput(
                    r.inscricaoId(), r.presente(), r.hashAssinaturaCandidato(), r.documentoApresentado()
                ))
                .toList();
            return new LogisticaUseCases.RegistrarListaPresencaCommand(salaId, operadorId, itens);
        }
    }

    public record ListaPresencaResponse(UUID salaId, int totalPresentes, int totalAusentes, Instant registradoEm) {
        public static ListaPresencaResponse from(LogisticaUseCases.ListaPresencaRegistradaOutput out) {
            return new ListaPresencaResponse(out.salaId(), out.totalPresentes(), out.totalAusentes(), out.registradoEm());
        }
    }
}
