package br.com.keyfalls.apuracao.interfaces.rest.dto;

import br.com.keyfalls.apuracao.application.ApuracaoUseCases;
import br.com.keyfalls.shared.domain.Alternativa;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApuracaoDtos {

    private ApuracaoDtos() {}

    public record ImportacaoFichasResponse(
        UUID protocoloImportacaoId,
        UUID concursoId,
        int totalFichasImportadas,
        Instant recebidoEm
    ) {
        public static ImportacaoFichasResponse from(ApuracaoUseCases.ImportacaoFichasOutput out) {
            return new ImportacaoFichasResponse(
                out.protocoloImportacaoId(), out.concursoId(), out.totalFichasImportadas(), out.recebidoEm()
            );
        }
    }

    public record ApuracaoDisparadaResponse(
        UUID concursoId,
        int totalCandidatosProcessados,
        int totalClassificados,
        Instant disparadoEm
    ) {
        public static ApuracaoDisparadaResponse from(ApuracaoUseCases.DispararApuracaoOutput out) {
            return new ApuracaoDisparadaResponse(
                out.concursoId(), out.totalCandidatosProcessados(), out.totalClassificados(), out.disparadoEm()
            );
        }
    }

    public record CandidatoClassificadoResponse(
        int posicao,
        String numeroInscricao,
        String nomeCandidato,
        UUID cargoId,
        BigDecimal notaFinalPonderada,
        int totalAcertos
    ) {
        public static CandidatoClassificadoResponse from(ApuracaoUseCases.ClassificadoItemOutput out) {
            return new CandidatoClassificadoResponse(
                out.posicao(), out.numeroInscricao(), out.nomeCandidato(),
                out.cargoId(), out.notaFinalPonderada(), out.totalAcertos()
            );
        }
    }

    public record NotaProvaIndividualResponse(
        UUID provaId,
        String disciplina,
        BigDecimal peso,
        int acertos,
        int erros,
        int totalQuestoesConsideradas,
        BigDecimal percentualAcertos,
        BigDecimal notaProva
    ) {}

    public record BoletimDesempenhoResponse(
        UUID inscricaoId,
        String numeroInscricao,
        UUID concursoId,
        UUID cargoId,
        List<NotaProvaIndividualResponse> notasPorProva,
        BigDecimal notaTotalPonderada,
        BigDecimal percentualTotalPonderado,
        int totalAcertos,
        int totalErros,
        String situacao
    ) {
        public static BoletimDesempenhoResponse from(ApuracaoUseCases.BoletimDesempenhoCandidatoOutput out) {
            var notas = out.notasPorProva().stream()
                .map(n -> new NotaProvaIndividualResponse(
                    n.provaId(), n.disciplina(), n.peso(), n.acertos(),
                    n.erros(), n.totalQuestoesConsideradas(), n.percentualAcertos(), n.notaProva()
                ))
                .toList();
            return new BoletimDesempenhoResponse(
                out.inscricaoId(), out.numeroInscricao(), out.concursoId(), out.cargoId(),
                notas, out.notaTotalPonderada(), out.percentualTotalPonderado(),
                out.totalAcertos(), out.totalErros(), out.situacao()
            );
        }
    }

    public record ItemGabaritoResponse(
        int numeroOrdem,
        UUID questaoId,
        Alternativa alternativaCorreta,
        boolean anulada
    ) {}

    public record GabaritoVigenteResponse(
        UUID provaId,
        boolean definitivo,
        List<ItemGabaritoResponse> itens
    ) {
        public static GabaritoVigenteResponse from(ApuracaoUseCases.GabaritoVigenciaOutput out) {
            var itens = out.itens().stream()
                .map(i -> new ItemGabaritoResponse(i.numeroOrdem(), i.questaoId(), i.alternativaCorreta(), i.anulada()))
                .toList();
            return new GabaritoVigenteResponse(out.provaId(), out.definitivo(), itens);
        }
    }
}
