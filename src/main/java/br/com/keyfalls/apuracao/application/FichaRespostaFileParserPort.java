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
 * Etapa 6 — Porta de parsing resiliente em streaming para importacao em lote de fichas
 * de resposta opticas (CSV ou layout posicional da leitora optica), evitando estouro de
 * memoria (OutOfMemoryError) em certames nacionais com centenas de milhares de candidatos.
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
        long totalFichasValidas,
        List<FalhaLinhaImportacao> falhasIsoladas
    ) {}

    /**
     * Processa o InputStream linha a linha e entrega blocos de tamanho 'tamanhoChunk'
     * ao consumidor de persistencia em batch, isolando linhas corrompidas no relatorio.
     */
    RelatorioParsingLote processarEmChunks(
        InputStream streamArquivo,
        int tamanhoChunk,
        Consumer<List<FichaCapturadaItem>> consumidorDeChunkValido
    );
}
