package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.FichaDeRespostaId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraDeNotaEClassificadorTest {

    private final CalculadoraDeNota calculadora = new CalculadoraDeNota();
    private final ClassificadorDeCandidatos classificador = new ClassificadorDeCandidatos();

    static Stream<Arguments> cenariosCorteECalculoPonderado() {
        return Stream.of(
            // 1. Nota geral alta (83.67%), mas reprovado por fazer 49% (49/100) na Prova 1 (Peso 1 vs Peso 5)
            Arguments.of(
                "Reprova candidato com media global alta (>80%), porem 49% em uma prova especifica",
                49, 100, 1,   // Prova 1: 49% (< 50%)
                91, 100, 5,   // Prova 2: 91%
                new BigDecimal("84.00"),
                SituacaoResultado.DESCLASSIFICADO
            ),
            // 2. Aprovado nas individuais (50% e 55%), mas reprovado no corte global ponderado (< 60%)
            Arguments.of(
                "Reprova candidato com >=50% em cada prova, mas media ponderada abaixo de 60% (53.33%)",
                50, 100, 1,   // Prova 1: 50%
                55, 100, 2,   // Prova 2: 55%
                new BigDecimal("53.33"),
                SituacaoResultado.DESCLASSIFICADO
            ),
            // 3. Limite exato de fronteira: 50.00% na Prova 1 (Peso 1) e 65.00% na Prova 2 (Peso 2) -> 60.00% exato
            Arguments.of(
                "Classifica candidato exatamente nos limites de corte (50.00% individual e 60.00% ponderado)",
                50, 100, 1,
                65, 100, 2,
                new BigDecimal("60.00"),
                SituacaoResultado.CLASSIFICADO
            )
        );
    }

    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("cenariosCorteECalculoPonderado")
    void deveValidarRegrasDeCorteIndividualEPonderado(
        String descricao,
        int acertosP1, int totalP1, int pesoP1,
        int acertosP2, int totalP2, int pesoP2,
        BigDecimal notaPonderadaEsperada,
        SituacaoResultado situacaoEsperada
    ) {
        ProvaId p1 = ProvaId.novo();
        ProvaId p2 = ProvaId.novo();

        var m1 = criarMatriz(p1, pesoP1, totalP1, 0);
        var m2 = criarMatriz(p2, pesoP2, totalP2, 0);

        FichaDeResposta ficha = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(),
            InscricaoId.novo(),
            ConcursoId.novo(),
            Instant.now(),
            Map.of(
                p1, gerarMarcacoesComAcertos(m1.gabaritoOficial(), acertosP1),
                p2, gerarMarcacoesComAcertos(m2.gabaritoOficial(), acertosP2)
            )
        );

        Resultado resultado = calculadora.apurarResultado(
            ficha, CargoId.novo(), List.of(m1, m2), new PoliticaAnulacaoQuestao.PontoUniversalStrategy()
        );

        assertThat(resultado.notaTotalPonderada()).isEqualByComparingTo(notaPonderadaEsperada);
        assertThat(resultado.situacao()).isEqualTo(situacaoEsperada);
    }

    @Test
    @DisplayName("Deve comparar impacto de questao anulada entre PontoParaTodos e DesconsiderarDoTotalDaProva")
    void deveAplicarPoliticasDeAnulacaoCorretamente() {
        ProvaId provaId = ProvaId.novo();
        // 10 questoes, 2 anuladas; candidato acertou 4 das 8 validas
        var matriz = criarMatriz(provaId, 1, 10, 2);
        List<QuestaoId> validas = matriz.gabaritoOficial().keySet().stream()
            .filter(q -> !matriz.questoesAnuladas().contains(q))
            .toList();

        Map<QuestaoId, Alternativa> marcacoes = new LinkedHashMap<>();
        for (int i = 0; i < validas.size(); i++) {
            marcacoes.put(validas.get(i), i < 4 ? Alternativa.A : Alternativa.B);
        }
        matriz.questoesAnuladas().forEach(q -> marcacoes.put(q, Alternativa.E));

        FichaDeResposta ficha = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(), InscricaoId.novo(), ConcursoId.novo(), Instant.now(), Map.of(provaId, marcacoes)
        );

        // PontoUniversalStrategy: (4 + 2) / 10 = 60.00% -> CLASSIFICADO
        Resultado comPontoParaTodos = calculadora.apurarResultado(
            ficha, CargoId.novo(), List.of(matriz), new PoliticaAnulacaoQuestao.PontoUniversalStrategy()
        );
        assertThat(comPontoParaTodos.notaTotalPonderada()).isEqualByComparingTo("60.00");
        assertThat(comPontoParaTodos.situacao()).isEqualTo(SituacaoResultado.CLASSIFICADO);

        // RedistribuicaoPesoStrategy: 4 / 8 = 50.00% (< 60% global) -> DESCLASSIFICADO
        Resultado desconsiderandoDoTotal = calculadora.apurarResultado(
            ficha, CargoId.novo(), List.of(matriz), new PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy()
        );
        assertThat(desconsiderandoDoTotal.notaTotalPonderada()).isEqualByComparingTo("50.00");
        assertThat(desconsiderandoDoTotal.situacao()).isEqualTo(SituacaoResultado.DESCLASSIFICADO);
    }

    @Test
    @DisplayName("Deve lancar excecao se todas as questoes da prova forem anuladas na politica RedistribuicaoPesoStrategy")
    void deveRejeitarProvaComTodasAsQuestoesAnuladasAoDesconsiderarDoTotal() {
        ProvaId provaId = ProvaId.novo();
        var matriz = criarMatriz(provaId, 1, 5, 5);
        FichaDeResposta ficha = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(), InscricaoId.novo(), ConcursoId.novo(), Instant.now(),
            Map.of(provaId, gerarMarcacoesComAcertos(matriz.gabaritoOficial(), 0))
        );

        assertThatThrownBy(() -> calculadora.apurarResultado(
            ficha, CargoId.novo(), List.of(matriz), new PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy()
        )).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    @DisplayName("Classificador deve excluir desclassificados e ordenar aprovados em ordem decrescente com desempate por acertos")
    void deveOrdenarApenasClassificadosComDesempateDeterministico() {
        ConcursoId concursoId = ConcursoId.novo();
        CargoId cargoId = CargoId.novo();
        ProvaId p1 = ProvaId.novo();
        ProvaId p2 = ProvaId.novo();

        // Candidato A: 80.00 ponderado (16 acertos totais)
        Resultado candA = Resultado.consolidar(
            br.com.keyfalls.shared.domain.ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(NotaProva.criar(p1, Peso.de(1), 8, 2, 10), NotaProva.criar(p2, Peso.de(1), 8, 2, 10))
        );
        // Candidato B: 80.00 ponderado, mas mais acertos absolutos (24 acertos totais) -> vence desempate!
        Resultado candB = Resultado.consolidar(
            br.com.keyfalls.shared.domain.ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(NotaProva.criar(p1, Peso.de(1), 8, 2, 10), NotaProva.criar(p2, Peso.de(1), 16, 4, 20))
        );
        // Candidato C: DESCLASSIFICADO (40% em P1, 100% em P2 Peso 3 -> 85.00 ponderado, mas cortado!)
        Resultado candCDesclassificado = Resultado.consolidar(
            br.com.keyfalls.shared.domain.ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(NotaProva.criar(p1, Peso.de(1), 4, 6, 10), NotaProva.criar(p2, Peso.de(3), 10, 0, 10))
        );

        var ranking = classificador.classificarAprovados(List.of(candA, candCDesclassificado, candB));

        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).inscricaoId()).isEqualTo(candB.inscricaoId());
        assertThat(ranking.get(0).colocacao()).isEqualTo(1);
        assertThat(ranking.get(1).inscricaoId()).isEqualTo(candA.inscricaoId());
        assertThat(ranking.get(1).colocacao()).isEqualTo(2);
    }

    private static CalculadoraDeNota.MatrizGabaritoProva criarMatriz(
        ProvaId provaId, int peso, int totalQuestoes, int qtdAnuladas
    ) {
        Map<QuestaoId, Alternativa> gabarito = new LinkedHashMap<>();
        Set<QuestaoId> anuladas = new java.util.LinkedHashSet<>();
        for (int i = 0; i < totalQuestoes; i++) {
            QuestaoId qId = QuestaoId.novo();
            gabarito.put(qId, Alternativa.A);
            if (i < qtdAnuladas) {
                anuladas.add(qId);
            }
        }
        return new CalculadoraDeNota.MatrizGabaritoProva(provaId, Peso.de(peso), gabarito, anuladas);
    }

    private static Map<QuestaoId, Alternativa> gerarMarcacoesComAcertos(
        Map<QuestaoId, Alternativa> gabarito, int qtdAcertos
    ) {
        Map<QuestaoId, Alternativa> marcacoes = new LinkedHashMap<>();
        int idx = 0;
        for (QuestaoId qId : gabarito.keySet()) {
            marcacoes.put(qId, idx < qtdAcertos ? Alternativa.A : Alternativa.C);
            idx++;
        }
        return marcacoes;
    }
}
