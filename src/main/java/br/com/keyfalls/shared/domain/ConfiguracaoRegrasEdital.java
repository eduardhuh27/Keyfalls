package br.com.keyfalls.shared.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Value Object imutavel persistido dentro da raiz de agregado Concurso,
 * encapsulando as 5 decisoes parametrizaveis de cada edital.
 */
public record ConfiguracaoRegrasEdital(
    PoliticaAnulacaoQuestao politicaAnulacaoQuestao,
    List<TipoCriterioDesempate> cadeiaDesempate,
    FormatoArquivoOptico formatoArquivoOptico,
    Duration prazoAberturaRecursoAposGabarito,
    boolean limitarUmRecursoPorQuestaoPorCandidato,
    Integer limiteMaximoRecursosPorCandidato,
    boolean agruparRecursosIdenticosEmLote,
    Set<ModalidadeIsencao> modalidadesIsencaoAceitas,
    boolean validacaoAutomatizadaCadUnico
) {

    public ConfiguracaoRegrasEdital {
        Objects.requireNonNull(politicaAnulacaoQuestao, "Politica de anulacao obrigatoria.");
        Objects.requireNonNull(cadeiaDesempate, "Cadeia de criterios de desempate obrigatoria.");
        if (cadeiaDesempate.isEmpty()) {
            throw new RegraDeNegocioException("O edital deve definir ao menos um criterio de desempate.");
        }
        Objects.requireNonNull(formatoArquivoOptico, "Formato do arquivo da leitora optica obrigatorio.");
        Objects.requireNonNull(prazoAberturaRecursoAposGabarito, "Prazo de abertura de recursos obrigatorio.");
        if (prazoAberturaRecursoAposGabarito.isNegative() || prazoAberturaRecursoAposGabarito.isZero()) {
            throw new RegraDeNegocioException("Prazo recursal deve ser estritamente positivo.");
        }
        if (limiteMaximoRecursosPorCandidato != null && limiteMaximoRecursosPorCandidato <= 0) {
            throw new RegraDeNegocioException("Quando informado, o limite maximo de recursos deve ser positivo.");
        }
        Objects.requireNonNull(modalidadesIsencaoAceitas, "Conjunto de modalidades de isencao obrigatorio.");
        if (modalidadesIsencaoAceitas.isEmpty()) {
            throw new RegraDeNegocioException("O edital deve contemplar ao menos uma modalidade legal de isencao.");
        }

        cadeiaDesempate = List.copyOf(cadeiaDesempate);
        modalidadesIsencaoAceitas = Set.copyOf(modalidadesIsencaoAceitas);
    }

    /**
     * Recomendacao Padrao (Default de Mercado para Concursos Publicos Federais/Estaduais).
     */
    public static ConfiguracaoRegrasEdital padraoRecomendado() {
        return new ConfiguracaoRegrasEdital(
            new PoliticaAnulacaoQuestao.PontoUniversalStrategy(),
            List.of(
                TipoCriterioDesempate.ESTATUTO_IDOSO_60_ANOS_OU_MAIS,
                TipoCriterioDesempate.MAIOR_NOTA_CONHECIMENTOS_ESPECIFICOS,
                TipoCriterioDesempate.MAIOR_NOTA_LINGUA_PORTUGUESA,
                TipoCriterioDesempate.MAIOR_TOTAL_ACERTOS,
                TipoCriterioDesempate.EXERCICIO_FUNCAO_JURADO,
                TipoCriterioDesempate.MAIOR_IDADE_EM_DIAS
            ),
            FormatoArquivoOptico.CSV_PONTO_E_VIRGULA,
            Duration.ofHours(48),
            true,
            null, // Sem teto global artificial para nao ferir direito constitucional de peticao
            true,
            Set.of(ModalidadeIsencao.CAD_UNICO_BAIXA_RENDA, ModalidadeIsencao.DOADOR_MEDULA_OSSEA),
            false
        );
    }
}
