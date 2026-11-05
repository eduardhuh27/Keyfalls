package br.com.keyfalls.apuracao.infrastructure.parser;

import br.com.keyfalls.apuracao.application.FichaRespostaFileParserPort;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Etapa 6 — Implementacao em streaming (O(chunkSize) em heap) para arquivos CSV da leitora optica.
 * Formato esperado por linha: inscricaoUUID;provaUUID;questao1UUID:A,questao2UUID:C,questao3UUID:-
 * ('-' ou vazio representa questao em branco / dupla marcacao invalidada).
 */
@Component
public class StreamingCsvFichaRespostaParserAdapter implements FichaRespostaFileParserPort {

    private static final int LIMITE_MAXIMO_ERROS_RELATORIO = 1_000;

    @Override
    public RelatorioParsingLote processarEmChunks(
        InputStream streamArquivo,
        int tamanhoChunk,
        Consumer<List<FichaCapturadaItem>> consumidorDeChunkValido
    ) {
        if (tamanhoChunk <= 0) {
            throw new IllegalArgumentException("O tamanho do chunk deve ser positivo.");
        }

        List<FichaCapturadaItem> bufferChunk = new ArrayList<>(tamanhoChunk);
        List<FalhaLinhaImportacao> falhas = new ArrayList<>();
        long numeroLinha = 0;
        long totalValidas = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamArquivo, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                if (linha.isBlank() || (numeroLinha == 1 && linha.startsWith("inscricao_id;"))) {
                    continue; // Ignora cabecalho opcional ou linha vazia final
                }

                try {
                    FichaCapturadaItem item = parsearLinha(numeroLinha, linha);
                    bufferChunk.add(item);
                    totalValidas++;

                    if (bufferChunk.size() >= tamanhoChunk) {
                        consumidorDeChunkValido.accept(List.copyOf(bufferChunk));
                        bufferChunk.clear();
                    }
                } catch (Exception ex) {
                    if (falhas.size() < LIMITE_MAXIMO_ERROS_RELATORIO) {
                        falhas.add(new FalhaLinhaImportacao(numeroLinha, truncar(linha), ex.getMessage()));
                    }
                }
            }

            if (!bufferChunk.isEmpty()) {
                consumidorDeChunkValido.accept(List.copyOf(bufferChunk));
                bufferChunk.clear();
            }
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha de I/O durante leitura em streaming das fichas: " + e.getMessage());
        }

        return new RelatorioParsingLote(numeroLinha, totalValidas, List.copyOf(falhas));
    }

    private static FichaCapturadaItem parsearLinha(long numeroLinha, String linha) {
        String[] colunas = linha.split(";", -1);
        if (colunas.length != 3) {
            throw new IllegalArgumentException("Esperadas 3 colunas separadas por ';' na linha " + numeroLinha);
        }

        InscricaoId inscricaoId = new InscricaoId(UUID.fromString(colunas[0].trim()));
        ProvaId provaId = new ProvaId(UUID.fromString(colunas[1].trim()));
        Map<QuestaoId, Alternativa> marcacoes = new LinkedHashMap<>();

        for (String par : colunas[2].split(",")) {
            String[] kv = par.trim().split(":", -1);
            if (kv.length != 2) {
                throw new IllegalArgumentException("Par questao:alternativa malformado: " + par);
            }
            QuestaoId questaoId = new QuestaoId(UUID.fromString(kv[0].trim()));
            String valorMarcado = kv[1].trim();
            Alternativa alternativa = (valorMarcado.isEmpty() || "-".equals(valorMarcado))
                ? null
                : Alternativa.valueOf(valorMarcado.toUpperCase());
            marcacoes.put(questaoId, alternativa);
        }

        return new FichaCapturadaItem(numeroLinha, inscricaoId, provaId, marcacoes);
    }

    private static String truncar(String texto) {
        return texto.length() <= 160 ? texto : texto.substring(0, 160) + "...";
    }
}
