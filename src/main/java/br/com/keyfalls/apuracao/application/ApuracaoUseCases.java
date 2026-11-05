package br.com.keyfalls.apuracao.application;

import br.com.keyfalls.shared.domain.Alternativa;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Portas de entrada (Inbound Ports) da camada application para o contexto Apuracao.
 * Note que ImportarFichasOpticasCommand recebe byte[] e metadados primitivos, mantendo
 * a camada application desacoplada de org.springframework.web.multipart.MultipartFile.
 */
public interface ApuracaoUseCases {

    record ImportarFichasOpticasCommand(
        UUID concursoId,
        String nomeArquivoOriginal,
        String contentType,
        byte[] conteudoBytes
    ) {}

    record ImportacaoFichasOutput(
        UUID protocoloImportacaoId,
        UUID concursoId,
        int totalFichasImportadas,
        Instant recebidoEm
    ) {}

    record DispararApuracaoOutput(
        UUID concursoId,
        int totalCandidatosProcessados,
        int totalClassificados,
        Instant disparadoEm
    ) {}

    record ClassificadoItemOutput(
        int posicao,
        String numeroInscricao,
        String nomeCandidato,
        UUID cargoId,
        BigDecimal notaFinalPonderada,
        int totalAcertos
    ) {}

    record DesempenhoProvaOutput(
        UUID provaId,
        String disciplina,
        BigDecimal peso,
        int acertos,
        int erros,
        int totalQuestoesConsideradas,
        BigDecimal percentualAcertos,
        BigDecimal notaProva
    ) {}

    record BoletimDesempenhoCandidatoOutput(
        UUID candidatoId,
        UUID inscricaoId,
        String numeroInscricao,
        UUID concursoId,
        UUID cargoId,
        List<DesempenhoProvaOutput> notasPorProva,
        BigDecimal notaTotalPonderada,
        BigDecimal percentualTotalPonderado,
        int totalAcertos,
        int totalErros,
        String situacao
    ) {}

    record ItemGabaritoVigenciaOutput(
        int numeroOrdem,
        UUID questaoId,
        Alternativa alternativaCorreta,
        boolean anulada
    ) {}

    record GabaritoVigenciaOutput(
        UUID provaId,
        boolean definitivo,
        List<ItemGabaritoVigenciaOutput> itens
    ) {}

    ImportacaoFichasOutput importarFichasOpticas(ImportarFichasOpticasCommand command);

    DispararApuracaoOutput processarApuracaoEClassificacao(UUID concursoId);

    List<ClassificadoItemOutput> listarClassificados(UUID concursoId, UUID cargoIdOpcional);

    BoletimDesempenhoCandidatoOutput consultarMeuDesempenho(UUID candidatoAutenticadoId, UUID concursoId);

    GabaritoVigenciaOutput consultarGabaritoVigente(UUID provaId);
}
