package br.com.keyfalls.logistica.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Portas de entrada (Inbound Ports) da camada application para o contexto Logistica.
 */
public interface LogisticaUseCases {

    record SalaCadastroInput(
        String identificacao,
        int capacidadeMaxima,
        UUID cargoAtendidoId,
        String nomeFiscal,
        String cpfFiscal
    ) {}

    record CadastrarLocalDeProvaCommand(
        UUID concursoId,
        String nomeInstituicao,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep,
        Double latitude,
        Double longitude,
        List<SalaCadastroInput> salas
    ) {}

    record LocalDeProvaCriadoOutput(
        UUID localDeProvaId,
        String nomeInstituicao,
        List<UUID> salasIds
    ) {}

    record AlocacaoEmLoteOutput(
        UUID concursoId,
        int totalCandidatosAlocados,
        Instant processadoEm
    ) {}

    record RegistroPresencaItemInput(
        UUID inscricaoId,
        boolean presente,
        String hashAssinaturaCandidato,
        String documentoApresentado
    ) {}

    record RegistrarListaPresencaCommand(
        UUID salaId,
        UUID operadorAutenticadoId,
        List<RegistroPresencaItemInput> registros
    ) {}

    record ListaPresencaRegistradaOutput(
        UUID salaId,
        int totalPresentes,
        int totalAusentes,
        Instant registradoEm
    ) {}

    LocalDeProvaCriadoOutput cadastrarLocalSalasEFiscais(CadastrarLocalDeProvaCommand command);

    AlocacaoEmLoteOutput executarAlocacaoPorProximidade(UUID concursoId);

    ListaPresencaRegistradaOutput registrarListaDePresenca(RegistrarListaPresencaCommand command);
}
