package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.ResultadoId;
import br.com.keyfalls.shared.domain.TipoCriterioDesempate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Domain Service que filtra os candidatos aprovados (CLASSIFICADO) e os ordena
 * em ordem decrescente de nota ponderada.
 */
public final class ClassificadorDeCandidatos {

    public record ItemClassificacao(
        int colocacao,
        InscricaoId inscricaoId,
        ResultadoId resultadoId,
        BigDecimal notaTotalPonderada,
        int totalAcertos
    ) {}

    public List<ItemClassificacao> classificarAprovados(List<Resultado> resultadosApurados) {
        Objects.requireNonNull(resultadosApurados, "Lista de resultados apurados obrigatoria.");

        Comparator<Resultado> criterioMeritocratico = Comparator
            .comparing(Resultado::notaTotalPonderada, Comparator.reverseOrder())
            .thenComparing(Comparator.comparingInt(Resultado::totalAcertos).reversed())
            .thenComparing(r -> r.inscricaoId().valor());

        List<Resultado> aprovadosOrdenados = resultadosApurados.stream()
            .filter(Resultado::estaClassificado)
            .sorted(criterioMeritocratico)
            .toList();

        List<ItemClassificacao> ranking = new ArrayList<>(aprovadosOrdenados.size());
        for (int i = 0; i < aprovadosOrdenados.size(); i++) {
            Resultado r = aprovadosOrdenados.get(i);
            ranking.add(new ItemClassificacao(
                i + 1,
                r.inscricaoId(),
                r.id(),
                r.notaTotalPonderada(),
                r.totalAcertos()
            ));
        }

        return Collections.unmodifiableList(ranking);
    }

    /**
     * Classificacao completa parametrizada pela cadeia de desempate do edital
     * (Estatuto do Idoso, notas por disciplina prioritaria, jurado e data de nascimento).
     */
    public List<ItemClassificacao> classificarComCriteriosDoEdital(
        List<CriterioDesempateChain.ResultadoCandidato> candidatosAvaliados,
        List<TipoCriterioDesempate> cadeiaDesempateEdital
    ) {
        Objects.requireNonNull(candidatosAvaliados, "Lista de candidatos avaliados obrigatoria.");
        Comparator<CriterioDesempateChain.ResultadoCandidato> comparadorEdital =
            CriterioDesempateChain.construir(cadeiaDesempateEdital);

        List<CriterioDesempateChain.ResultadoCandidato> aprovadosOrdenados = candidatosAvaliados.stream()
            .filter(rc -> rc.resultado().estaClassificado())
            .sorted(comparadorEdital)
            .toList();

        List<ItemClassificacao> ranking = new ArrayList<>(aprovadosOrdenados.size());
        for (int i = 0; i < aprovadosOrdenados.size(); i++) {
            Resultado r = aprovadosOrdenados.get(i).resultado();
            ranking.add(new ItemClassificacao(
                i + 1,
                r.inscricaoId(),
                r.id(),
                r.notaTotalPonderada(),
                r.totalAcertos()
            ));
        }
        return Collections.unmodifiableList(ranking);
    }
}
