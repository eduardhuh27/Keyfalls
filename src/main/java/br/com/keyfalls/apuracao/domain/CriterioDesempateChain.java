package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.TipoCriterioDesempate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Cadeia fluente e configuravel de criterios legais de desempate (Comparator Composto).
 * Aplica nota final ponderada seguida estritamente da ordem parametrizada no edital
 * (Estatuto do Idoso Lei 10.741/2003, notas por prova chave, funcao de jurado e idade exata).
 */
public final class CriterioDesempateChain {

    private static final int IDADE_ESTATUTO_IDOSO = 60;

    public record ResultadoCandidato(
        Resultado resultado,
        LocalDate dataNascimento,
        boolean exerceuFuncaoJurado,
        ProvaId provaConhecimentosEspecificosId,
        ProvaId provaLinguaPortuguesaId,
        LocalDate dataReferenciaIdadeEdital
    ) {
        public ResultadoCandidato {
            Objects.requireNonNull(resultado, "Resultado apurado obrigatorio.");
            Objects.requireNonNull(dataNascimento, "Data de nascimento do candidato obrigatoria para desempate.");
            Objects.requireNonNull(dataReferenciaIdadeEdital, "Data de referencia do edital para calculo de idade obrigatoria.");
        }

        public boolean amparadoPeloEstatutoDoIdoso() {
            return Period.between(dataNascimento, dataReferenciaIdadeEdital).getYears() >= IDADE_ESTATUTO_IDOSO;
        }

        public BigDecimal notaDaProva(ProvaId provaId) {
            if (provaId == null) {
                return BigDecimal.ZERO;
            }
            return resultado.notasPorProva().stream()
                .filter(n -> n.provaId().equals(provaId))
                .map(NotaProva::notaNaEscalaCem)
                .findFirst()
                .orElse(BigDecimal.ZERO);
        }
    }

    private CriterioDesempateChain() {}

    public static Comparator<ResultadoCandidato> construir(List<TipoCriterioDesempate> ordemCriteriosEdital) {
        Objects.requireNonNull(ordemCriteriosEdital, "Ordem de criterios de desempate obrigatoria.");

        // 1. Criterio soberano: Maior Nota Total Ponderada (decrescente)
        Comparator<ResultadoCandidato> comparador = Comparator.comparing(
            rc -> rc.resultado().notaTotalPonderada(),
            Comparator.reverseOrder()
        );

        // 2. Encadeia dinamicamente cada criterio configurado no edital do Concurso
        for (TipoCriterioDesempate criterio : ordemCriteriosEdital) {
            comparador = comparador.thenComparing(traduzirCriterio(criterio));
        }

        // 3. Fallback deterministico final por InscricaoId (evita ordenacao instavel)
        return comparador.thenComparing(rc -> rc.resultado().inscricaoId().valor());
    }

    private static Comparator<ResultadoCandidato> traduzirCriterio(TipoCriterioDesempate criterio) {
        return switch (criterio) {
            case ESTATUTO_IDOSO_60_ANOS_OU_MAIS ->
                Comparator.comparing(ResultadoCandidato::amparadoPeloEstatutoDoIdoso).reversed();

            case MAIOR_NOTA_CONHECIMENTOS_ESPECIFICOS ->
                Comparator.comparing(
                    (ResultadoCandidato rc) -> rc.notaDaProva(rc.provaConhecimentosEspecificosId()),
                    Comparator.reverseOrder()
                );

            case MAIOR_NOTA_LINGUA_PORTUGUESA ->
                Comparator.comparing(
                    (ResultadoCandidato rc) -> rc.notaDaProva(rc.provaLinguaPortuguesaId()),
                    Comparator.reverseOrder()
                );

            case MAIOR_TOTAL_ACERTOS ->
                Comparator.comparingInt((ResultadoCandidato rc) -> rc.resultado().totalAcertos()).reversed();

            case EXERCICIO_FUNCAO_JURADO ->
                Comparator.comparing(ResultadoCandidato::exerceuFuncaoJurado).reversed();

            case MAIOR_IDADE_EM_DIAS ->
                // Data de nascimento menor (mais antiga) significa candidato mais velho -> vem primeiro!
                Comparator.comparing(ResultadoCandidato::dataNascimento);
        };
    }
}
