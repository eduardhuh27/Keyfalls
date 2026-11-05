package br.com.keyfalls.shared.domain;

/**
 * Politica/Strategy selada para calculo de impacto de questoes anuladas por edital.
 */
public sealed interface PoliticaAnulacaoQuestao
    permits PoliticaAnulacaoQuestao.PontoUniversalStrategy,
            PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy {

    record ResultadoContabilizacaoAnulacao(int acertosFinais, int denominadorConsiderado) {}

    ResultadoContabilizacaoAnulacao contabilizar(
        int acertosEmQuestoesValidas,
        int quantidadeAnuladas,
        int totalOriginalQuestoes
    );

    /**
     * Opcao A (Default Recomendado): Atribui o acerto da questao anulada universalmente
     * a todos os candidatos presentes, mantendo o denominador original da prova.
     */
    record PontoUniversalStrategy() implements PoliticaAnulacaoQuestao {
        @Override
        public ResultadoContabilizacaoAnulacao contabilizar(
            int acertosEmQuestoesValidas,
            int quantidadeAnuladas,
            int totalOriginalQuestoes
        ) {
            return new ResultadoContabilizacaoAnulacao(
                acertosEmQuestoesValidas + quantidadeAnuladas,
                totalOriginalQuestoes
            );
        }
    }

    /**
     * Opcao B: Remove as questoes anuladas do denominador da prova, redistribuindo
     * proporcionalmente o peso da prova apenas entre as questoes validas remanescentes.
     */
    record RedistribuicaoPesoStrategy() implements PoliticaAnulacaoQuestao {
        @Override
        public ResultadoContabilizacaoAnulacao contabilizar(
            int acertosEmQuestoesValidas,
            int quantidadeAnuladas,
            int totalOriginalQuestoes
        ) {
            int totalValido = totalOriginalQuestoes - quantidadeAnuladas;
            if (totalValido <= 0) {
                throw new RegraDeNegocioException(
                    "Todas as questoes da prova foram anuladas; impossivel redistribuir peso sem zerar o denominador."
                );
            }
            return new ResultadoContabilizacaoAnulacao(acertosEmQuestoesValidas, totalValido);
        }
    }
}
