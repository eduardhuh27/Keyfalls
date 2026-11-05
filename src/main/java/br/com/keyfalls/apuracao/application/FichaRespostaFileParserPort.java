package br.com.keyfalls.apuracao.application;

import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Porta de parsing resiliente em streaming para importacao em lote de fichas
 * de resposta opticas (CSV ou layout posicional da leitora optica), evitando estouro de
 * memoria (OutOfMemoryError) em certames com grande volume de candidatos.
 */
public interface FichaRespostaFileParserPort {

    record FichaCapturadaItem(
        long numeroLinhaArquivo,
        InscricaoId inscricaoId,
        ProvaId provaId,
        Map<QuestaoId, Alternativa> marcacoes
    ) {}

    record FalhaLinhaImportacao(
        long numeroLinhaArquivo,
        String linhaBruta,
        String motivoRejeicao
    ) {}

    record RelatorioParsingLote(
        long totalLinhasLidas,
        long totalLinhasValidasProcessadas,
        List<FalhaLinhaImportacao> falhasPorLinha
    ) {
        public boolean houveFalhas() {
            return !falhasPorLinha.isEmpty();
        }
    }

    RelatorioParsingLote processarEmChunks(
        InputStream streamArquivo,
        int tamanhoChunk,
        Consumer<List<FichaCapturadaItem>> consumidorDeChunkValido
    );
}
