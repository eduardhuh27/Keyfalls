package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.ResultadoId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Domain Service que apura a FichaDeResposta frente aos gabaritos e pesos das provas,
 * aplicando a PoliticaAnulacaoQuestao parametrizada e gerando o agregado Resultado.
 */
public final class CalculadoraDeNota {

    public record MatrizGabaritoProva(
        ProvaId provaId,
        Peso peso,
        Map<QuestaoId, Alternativa> gabaritoOficial,
        Set<QuestaoId> questoesAnuladas
    ) {
        public MatrizGabaritoProva {
            Objects.requireNonNull(provaId, "ProvaId obrigatorio.");
            Objects.requireNonNull(peso, "Peso obrigatorio.");
            Objects.requireNonNull(gabaritoOficial, "Gabarito oficial obrigatorio.");
            if (gabaritoOficial.isEmpty()) {
                throw new RegraDeNegocioException("Gabarito oficial da prova nao pode ser vazio.");
            }
            Objects.requireNonNull(questoesAnuladas, "Conjunto de questoes anuladas obrigatorio.");
            gabaritoOficial = Collections.unmodifiableMap(new LinkedHashMap<>(gabaritoOficial));
            questoesAnuladas = Set.copyOf(questoesAnuladas);
        }
    }

    public Resultado apurarResultado(
        FichaDeResposta fichaDeResposta,
        CargoId cargoId,
        List<MatrizGabaritoProva> matrizesDasProvas,
        PoliticaAnulacaoQuestao politicaAnulacao
    ) {
        Objects.requireNonNull(fichaDeResposta, "FichaDeResposta obrigatoria.");
        Objects.requireNonNull(cargoId, "CargoId obrigatorio.");
        Objects.requireNonNull(matrizesDasProvas, "Matrizes de gabarito das provas obrigatorias.");
        Objects.requireNonNull(politicaAnulacao, "Politica de anulacao de questao obrigatoria.");

        if (matrizesDasProvas.isEmpty()) {
            throw new RegraDeNegocioException("Deve haver ao menos uma matriz de prova para apuracao.");
        }

        List<NotaProva> notasApuradas = new ArrayList<>();

        for (MatrizGabaritoProva matriz : matrizesDasProvas) {
            if (!fichaDeResposta.contemProva(matriz.provaId())) {
                throw new RegraDeNegocioException(
                    "Ficha de resposta nao contem captura para a prova obrigatoria: " + matriz.provaId().valor()
                );
            }
            notasApuradas.add(calcularNotaDaProva(fichaDeResposta, matriz, politicaAnulacao));
        }

        return Resultado.consolidar(
            ResultadoId.novo(),
            fichaDeResposta.inscricaoId(),
            fichaDeResposta.concursoId(),
            cargoId,
            notasApuradas
        );
    }

    private NotaProva calcularNotaDaProva(
        FichaDeResposta ficha,
        MatrizGabaritoProva matriz,
        PoliticaAnulacaoQuestao politicaAnulacao
    ) {
        int acertosEmQuestoesValidas = 0;
        int errosEmQuestoesValidas = 0;
        int quantidadeAnuladas = 0;

        for (Map.Entry<QuestaoId, Alternativa> item : matriz.gabaritoOficial().entrySet()) {
            QuestaoId questaoId = item.getKey();
            Alternativa respostaOficial = item.getValue();

            if (matriz.questoesAnuladas().contains(questaoId)) {
                quantidadeAnuladas++;
                continue;
            }

            Optional<Alternativa> respostaCandidato = ficha.respostaMarcada(matriz.provaId(), questaoId);
            if (respostaCandidato.isPresent() && respostaCandidato.get() == respostaOficial) {
                acertosEmQuestoesValidas++;
            } else {
                // Questao em branco ou marcacao divergente contabiliza erro
                errosEmQuestoesValidas++;
            }
        }

        int totalOriginal = matriz.gabaritoOficial().size();

        var contabilizacao = switch (politicaAnulacao) {
            case PoliticaAnulacaoQuestao.PontoUniversalStrategy s ->
                s.contabilizar(acertosEmQuestoesValidas, quantidadeAnuladas, totalOriginal);
            case PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy s ->
                s.contabilizar(acertosEmQuestoesValidas, quantidadeAnuladas, totalOriginal);
        };

        return NotaProva.criar(
            matriz.provaId(),
            matriz.peso(),
            contabilizacao.acertosFinais(),
            errosEmQuestoesValidas,
            contabilizacao.denominadorConsiderado()
        );
    }
}
