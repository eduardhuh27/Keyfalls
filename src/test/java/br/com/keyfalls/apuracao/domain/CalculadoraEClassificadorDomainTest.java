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
import br.com.keyfalls.shared.domain.ResultadoId;
import br.com.keyfalls.shared.domain.TipoCriterioDesempate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suite Adversarial (Bug Hunter) de Dominio Puro para CalculadoraDeNota, Resultado e ClassificadorDeCandidatos.
 * Zero mocks: instancia exclusivamente objetos ricos de dominio em Java 21.
 */
class CalculadoraEClassificadorDomainTest {

    private final CalculadoraDeNota calculadora = new CalculadoraDeNota();
    private final ClassificadorDeCandidatos classificador = new ClassificadorDeCandidatos();
// 1. DIZIMAS PERIODICAS E PRECISAO DECIMAL (ANTI-ARITHMETIC_EXCEPTION & ANTI-TRUNCAMENTO)
@ParameterizedTest(name = "[{index}] P1({0}/{1} peso {2}) + P2({3}/{4} peso {5}) => nota={6}, situacao={7}")
    @CsvSource({
        // 7 questoes (dizima 4/7 = 57.142857...%) com peso 1.5 e 3 questoes (2/3 = 66.666666...%) com peso 2.3
        // Media exata = ((57.1428571429 * 1.5) + (66.6666666667 * 2.3)) / 3.8 = 239.0476190478 / 3.8 = 62.9072... -> 62.91
        "4,  7, 1.50,  2,   3, 2.30,  62.91, CLASSIFICADO",

        // ARMADILHA DE ARREDONDAMENTO PREMATURO:
        // P1: 11/19 (57.8947368421...%) peso 2.30 | P2: 62/100 (62.00%) peso 2.41
        // Se truncar P1 prematuramente para 57.89 antes de ponderar: (57.89*2.30 + 62*2.41)/4.71 = 59.9929 -> 59.99 (FALSO REPROVADO!)
        // Com precisao intermediaria correta de 10 casas: (133.1578947368 + 149.42)/4.71 = 59.9953 -> 60.00 (CLASSIFICADO!)
        "11, 19, 2.30, 62, 100, 2.41, 60.00, CLASSIFICADO",

        // 7 questoes (3/7 = 42.8571...% < 50%) com peso 1.5 e 7 questoes (7/7 = 100%) com peso 2.3
        // Media global = 77.44%, mas reprovado na prova 1 (< 50%)
        "3,  7, 1.50,  7,   7, 2.30,  77.44, DESCLASSIFICADO",

        // 9 questoes (5/9 = 55.5555...%) peso 1.7 e 11 questoes (6/11 = 54.5454...%) peso 2.3
        // Ambas >= 50%, mas media ponderada = 54.97% (< 60%) -> DESCLASSIFICADO
        "5,  9, 1.70,  6,  11, 2.30,  54.97, DESCLASSIFICADO"
    })
    @DisplayName("Deve calcular dizimas periodicas e pesos quebrados sem ArithmeticException nem perda de centesimos")
    void deveCalcularDizimasPeriodicasEPesosQuebradosComPrecisaoExata(
        int acertosP1, int totalP1, String pesoP1,
        int acertosP2, int totalP2, String pesoP2,
        String notaEsperada, SituacaoResultado situacaoEsperada
    ) {
        ProvaId p1 = ProvaId.novo();
        ProvaId p2 = ProvaId.novo();

        var m1 = criarMatriz(p1, Peso.de(pesoP1), totalP1, 0);
        var m2 = criarMatriz(p2, Peso.de(pesoP2), totalP2, 0);

        FichaDeResposta ficha = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(),
            InscricaoId.novo(),
            ConcursoId.novo(),
            Instant.parse("2026-10-18T18:00:00Z"),
            Map.of(
                p1, marcarPrimeirasCorretas(m1.gabaritoOficial(), acertosP1),
                p2, marcarPrimeirasCorretas(m2.gabaritoOficial(), acertosP2)
            )
        );

        Resultado resultado = calculadora.apurarResultado(
            ficha, CargoId.novo(), List.of(m1, m2), new PoliticaAnulacaoQuestao.PontoUniversalStrategy()
        );

        assertThat(resultado.notaTotalPonderada()).isEqualByComparingTo(new BigDecimal(notaEsperada));
        assertThat(resultado.situacao()).isEqualTo(situacaoEsperada);
    }
// 2. FALSO APROVADO E FRONTEIRAS EXATAS DE CORTE (49.99% / 50.00% e 59.99% / 60.00%)
@ParameterizedTest(name = "[{index}] {0} => P1={1}%, P2={3}% => Total={5} ({6})")
    @CsvSource({
        // Falso aprovado classico: 49.90% em prova de peso minimo (1.0) e 92.02% em prova de peso 5.0 -> Media 85.00%, mas DESCLASSIFICADO!
        "'Falso Aprovado (85% global mas 49.9% individual)',     499,  1000, 1.00,  9202, 10000, 5.00, 85.00, DESCLASSIFICADO",
        // Fronteira inferior individual: 49.99% na Prova 1 e 100.00% na Prova 2 -> DESCLASSIFICADO!
        "'Fronteira 49.99% individual reprova mesmo com 75%',   4999, 10000, 1.00, 10000, 10000, 1.00, 75.00, DESCLASSIFICADO",
        // Fronteira exata 50.00% individual, mas 59.99% ponderado global -> DESCLASSIFICADO!
        "'Fronteira 50.00% individual mas 59.99% global',       5000, 10000, 1.00,  6998, 10000, 1.00, 59.99, DESCLASSIFICADO",
        // Fronteira exata de aprovacao dupla: 50.00% na Prova 1 e 70.00% na Prova 2 (pesos iguais) -> 60.00% CLASSIFICADO!
        "'Fronteira exata 50.00% individual e 60.00% global',   5000, 10000, 1.00,  7000, 10000, 1.00, 60.00, CLASSIFICADO"
    })
    @DisplayName("Deve aplicar rigorosamente as fronteiras de 49.99%/50.00% por prova e 59.99%/60.00% no total ponderado")
    void deveValidarFronteirasExatasEFalsosAprovados(
        String cenario,
        int acertosP1, int totalP1, String pesoP1,
        int acertosP2, int totalP2, String pesoP2,
        String mediaEsperada, SituacaoResultado situacaoEsperada
    ) {
        NotaProva notaP1 = NotaProva.criar(ProvaId.novo(), Peso.de(pesoP1), acertosP1, totalP1 - acertosP1, totalP1);
        NotaProva notaP2 = NotaProva.criar(ProvaId.novo(), Peso.de(pesoP2), acertosP2, totalP2 - acertosP2, totalP2);

        Resultado resultado = Resultado.consolidar(
            ResultadoId.novo(), InscricaoId.novo(), ConcursoId.novo(), CargoId.novo(), List.of(notaP1, notaP2)
        );

        assertThat(resultado.notaTotalPonderada()).as(cenario).isEqualByComparingTo(new BigDecimal(mediaEsperada));
        assertThat(resultado.situacao()).as(cenario).isEqualTo(situacaoEsperada);
    }
// 3. BUG CLASSICO DE ANULACAO: NAO DUPLICAR PONTO DE QUEM JA HAVIA ACERTADO!
@Test
    @DisplayName("Anular questao que o candidato ja havia acertado no gabarito preliminar NAO pode duplicar o ponto dele")
    void naoDeveDuplicarPontoQuandoQuestaoJaAcertadaPeloCandidatoForAnulada() {
        ProvaId provaId = ProvaId.novo();
        Map<QuestaoId, Alternativa> gabarito = new LinkedHashMap<>();
        List<QuestaoId> questoes = java.util.stream.IntStream.range(0, 10)
            .mapToObj(i -> QuestaoId.novo())
            .toList();
        questoes.forEach(q -> gabarito.put(q, Alternativa.A));

        // Candidato A acertou Q0 (que sera anulada) + Q1..Q5 (total = 6 acertos preliminares de 10 = 60.00%)
        Map<QuestaoId, Alternativa> marcacoesCandidatoQueJaAcertou = new LinkedHashMap<>();
        // Candidato B errou Q0 (marcou E) + acertou Q1..Q5 (total = 5 acertos preliminares de 10 = 50.00%)
        Map<QuestaoId, Alternativa> marcacoesCandidatoQueHaviaErrado = new LinkedHashMap<>();

        for (int i = 0; i < 10; i++) {
            QuestaoId q = questoes.get(i);
            if (i == 0) {
                marcacoesCandidatoQueJaAcertou.put(q, Alternativa.A);
                marcacoesCandidatoQueHaviaErrado.put(q, Alternativa.E);
            } else if (i <= 5) {
                marcacoesCandidatoQueJaAcertou.put(q, Alternativa.A);
                marcacoesCandidatoQueHaviaErrado.put(q, Alternativa.A);
            } else {
                marcacoesCandidatoQueJaAcertou.put(q, Alternativa.C);
                marcacoesCandidatoQueHaviaErrado.put(q, Alternativa.C);
            }
        }

        FichaDeResposta fichaCandA = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(), InscricaoId.novo(), ConcursoId.novo(), Instant.now(),
            Map.of(provaId, marcacoesCandidatoQueJaAcertou)
        );
        FichaDeResposta fichaCandB = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(), InscricaoId.novo(), ConcursoId.novo(), Instant.now(),
            Map.of(provaId, marcacoesCandidatoQueHaviaErrado)
        );

        // Gabarito apos anulacao de Q0
        var matrizComQ0Anulada = new CalculadoraDeNota.MatrizGabaritoProva(
            provaId, Peso.de(1), gabarito, Set.of(questoes.get(0))
        );
        var estrategiaUniversal = new PoliticaAnulacaoQuestao.PontoUniversalStrategy();

        Resultado posAnulacaoCandA = calculadora.apurarResultado(
            fichaCandA, CargoId.novo(), List.of(matrizComQ0Anulada), estrategiaUniversal
        );
        Resultado posAnulacaoCandB = calculadora.apurarResultado(
            fichaCandB, CargoId.novo(), List.of(matrizComQ0Anulada), estrategiaUniversal
        );

        // Candidato A permanece exatamente com 6 acertos (60.00%), JAMAIS 7 acertos (70.00%)!
        assertThat(posAnulacaoCandA.totalAcertos()).isEqualTo(6);
        assertThat(posAnulacaoCandA.notaTotalPonderada()).isEqualByComparingTo("60.00");

        // Candidato B sobe legitimamente de 5 para 6 acertos (60.00%)
        assertThat(posAnulacaoCandB.totalAcertos()).isEqualTo(6);
        assertThat(posAnulacaoCandB.notaTotalPonderada()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("RedistribuicaoPesoStrategy deve lancar excecao de dominio se 100% das questoes da prova forem anuladas")
    void deveFalharComSegurancaSeTodasQuestoesForemAnuladasNaRedistribuicao() {
        ProvaId provaId = ProvaId.novo();
        var matrizTotalmenteAnulada = criarMatriz(provaId, Peso.de("2.0"), 5, 5);
        FichaDeResposta ficha = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(), InscricaoId.novo(), ConcursoId.novo(), Instant.now(),
            Map.of(provaId, marcarPrimeirasCorretas(matrizTotalmenteAnulada.gabaritoOficial(), 0))
        );

        assertThatThrownBy(() -> calculadora.apurarResultado(
            ficha, CargoId.novo(), List.of(matrizTotalmenteAnulada), new PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy()
        ))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("impossivel redistribuir peso");
    }
// 4. EMPATE MULTIPLO EM CASCATA (ESTATUTO DO IDOSO -> PROVA MAIOR PESO -> IDADE EM DIAS)
@Test
    @DisplayName("Deve resolver empate multiplo na nota ponderada aplicando Estatuto do Idoso -> Especificas -> Data Nascimento exata")
    void deveDesempatarCascataCompletaComEstatutoDoIdosoEspecificasEIdadeEmDias() {
        ConcursoId concursoId = ConcursoId.novo();
        CargoId cargoId = CargoId.novo();
        ProvaId provaPortugues = ProvaId.novo();         // Peso 1
        ProvaId provaEspecificos = ProvaId.novo();       // Peso 3 (maior peso)
        LocalDate dataRefEdital = LocalDate.of(2026, 11, 4);

        // Todos os 4 aprovados abaixo empatam exatamente em 75.00 na media ponderada:
        // 1) Idoso (60 anos exatos na data de referencia): Portugues=75, Especificos=75 -> Media 75.00 (1o lugar pelo Estatuto do Idoso!)
        Resultado resIdoso = Resultado.consolidar(
            ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(
                NotaProva.criar(provaPortugues, Peso.de(1), 15, 5, 20),   // 75%
                NotaProva.criar(provaEspecificos, Peso.de(3), 15, 5, 20)  // 75% (menor que os jovens em Especificas, mas e Idoso!)
            )
        );

        // 2) Jovem Especialista (30 anos): Portugues=60, Especificos=80 -> (60*1 + 80*3)/4 = 300/4 = 75.00 (2o lugar por Especificos=80!)
        Resultado resJovemEspecialista = Resultado.consolidar(
            ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(
                NotaProva.criar(provaPortugues, Peso.de(1), 12, 8, 20),   // 60%
                NotaProva.criar(provaEspecificos, Peso.de(3), 16, 4, 20)  // 80%
            )
        );

        // 3) Adulto Mais Velho por 1 dia (nascido 1985-05-10): Portugues=75, Especificos=75 -> Media 75.00 (3o lugar!)
        Resultado resAdultoMaisVelho = Resultado.consolidar(
            ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(
                NotaProva.criar(provaPortugues, Peso.de(1), 15, 5, 20),
                NotaProva.criar(provaEspecificos, Peso.de(3), 15, 5, 20)
            )
        );

        // 4) Adulto Mais Novo por 1 dia (nascido 1985-05-11): Portugues=75, Especificos=75 -> Media 75.00 (4o lugar!)
        Resultado resAdultoMaisNovo = Resultado.consolidar(
            ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(
                NotaProva.criar(provaPortugues, Peso.de(1), 15, 5, 20),
                NotaProva.criar(provaEspecificos, Peso.de(3), 15, 5, 20)
            )
        );

        // Intruso Desclassificado com media altissima (86.25), mas 45% em Portugues -> Deve ser filtrado fora!
        Resultado resDesclassificadoNotaAlta = Resultado.consolidar(
            ResultadoId.novo(), InscricaoId.novo(), concursoId, cargoId,
            List.of(
                NotaProva.criar(provaPortugues, Peso.de(1), 9, 11, 20),    // 45% (< 50%!)
                NotaProva.criar(provaEspecificos, Peso.de(3), 20, 0, 20)   // 100%
            )
        );

        List<CriterioDesempateChain.ResultadoCandidato> avaliados = List.of(
            new CriterioDesempateChain.ResultadoCandidato(
                resAdultoMaisNovo, LocalDate.of(1985, 5, 11), false, provaEspecificos, provaPortugues, dataRefEdital
            ),
            new CriterioDesempateChain.ResultadoCandidato(
                resDesclassificadoNotaAlta, LocalDate.of(1950, 1, 1), true, provaEspecificos, provaPortugues, dataRefEdital
            ),
            new CriterioDesempateChain.ResultadoCandidato(
                resJovemEspecialista, LocalDate.of(1996, 8, 20), false, provaEspecificos, provaPortugues, dataRefEdital
            ),
            new CriterioDesempateChain.ResultadoCandidato(
                resIdoso, LocalDate.of(1966, 11, 4), false, provaEspecificos, provaPortugues, dataRefEdital // 60 anos cravados!
            ),
            new CriterioDesempateChain.ResultadoCandidato(
                resAdultoMaisVelho, LocalDate.of(1985, 5, 10), false, provaEspecificos, provaPortugues, dataRefEdital
            )
        );

        List<TipoCriterioDesempate> cadeiaEdital = List.of(
            TipoCriterioDesempate.ESTATUTO_IDOSO_60_ANOS_OU_MAIS,
            TipoCriterioDesempate.MAIOR_NOTA_CONHECIMENTOS_ESPECIFICOS,
            TipoCriterioDesempate.MAIOR_NOTA_LINGUA_PORTUGUESA,
            TipoCriterioDesempate.MAIOR_IDADE_EM_DIAS
        );

        var classificacao = classificador.classificarComCriteriosDoEdital(avaliados, cadeiaEdital);

        assertThat(classificacao).hasSize(4);
        assertThat(classificacao)
            .extracting(ClassificadorDeCandidatos.ItemClassificacao::inscricaoId)
            .containsExactly(
                resIdoso.inscricaoId(),
                resJovemEspecialista.inscricaoId(),
                resAdultoMaisVelho.inscricaoId(),
                resAdultoMaisNovo.inscricaoId()
            );
    }

    private static CalculadoraDeNota.MatrizGabaritoProva criarMatriz(
        ProvaId provaId, Peso peso, int totalQuestoes, int qtdAnuladas
    ) {
        Map<QuestaoId, Alternativa> gabarito = new LinkedHashMap<>();
        Set<QuestaoId> anuladas = new LinkedHashSet<>();
        for (int i = 0; i < totalQuestoes; i++) {
            QuestaoId qId = QuestaoId.novo();
            gabarito.put(qId, Alternativa.A);
            if (i < qtdAnuladas) {
                anuladas.add(qId);
            }
        }
        return new CalculadoraDeNota.MatrizGabaritoProva(provaId, peso, gabarito, anuladas);
    }

    private static Map<QuestaoId, Alternativa> marcarPrimeirasCorretas(
        Map<QuestaoId, Alternativa> gabarito, int qtdAcertos
    ) {
        Map<QuestaoId, Alternativa> marcacoes = new LinkedHashMap<>();
        int idx = 0;
        for (QuestaoId qId : gabarito.keySet()) {
            marcacoes.put(qId, idx < qtdAcertos ? Alternativa.A : Alternativa.D);
            idx++;
        }
        return marcacoes;
    }
}
