package br.com.keyfalls.apuracao.infrastructure.parser;

import br.com.keyfalls.apuracao.application.FichaRespostaFileParserPort;
import br.com.keyfalls.apuracao.application.FichaRespostaParserPort;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.FormatoArquivoOptico;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Adaptador Tecnico (Secondary Adapter) para parsing de arquivos CSV de leitura optica.
 * Suporta dois layouts CSV de leitoras opticas:
 * 1) Layout chave-valor detalhado: inscricaoId;provaId;questaoId1:A,questaoId2:*,questaoId3:
 * 2) Layout posicional por ordem: numeroInscricao;provaId;A;B;*; ;E
 *
 * Trata explicitamente:
 * - '*' (dupla marcacao na leitora optica) -> MarcacaoOpticaCapturada.duplaMarcacao() / Alternativa null (0 pontos)
 * - ' ' ou vazio (em branco) -> MarcacaoOpticaCapturada.emBranco() / Alternativa null (0 pontos)
 * - Linhas malformadas -> isoladas em FalhaLinhaImportacao sem abortar o chunk valido.
 */
@Primary
@Component
public class CsvFichaRespostaParserAdapter implements FichaRespostaParserPort, FichaRespostaFileParserPort {

    @Override
    public boolean suportaFormato(FormatoArquivoOptico formato) {
        return formato == FormatoArquivoOptico.CSV_DELIMITADO;
    }

    @Override
    public List<FichaOpticaLida> lerStream(InputStream streamArquivo, List<Integer> ordensEsperadasQuestoes) {
        Objects.requireNonNull(streamArquivo, "InputStream do arquivo optico obrigatorio.");
        Objects.requireNonNull(ordensEsperadasQuestoes, "Lista de ordens de questoes obrigatoria.");

        List<FichaOpticaLida> fichasLidas = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamArquivo, StandardCharsets.UTF_8))) {
            String linha;
            long numLinha = 0;
            while ((linha = reader.readLine()) != null) {
                numLinha++;
                if (linha.isBlank() || linha.trim().startsWith("#") || linha.toLowerCase().startsWith("numeroinscricao")) {
                    continue;
                }
                String[] colunas = linha.split(";", -1);
                if (colunas.length < 2 + ordensEsperadasQuestoes.size()) {
                    throw new RegraDeNegocioException(
                        "Linha " + numLinha + " do arquivo CSV optico possui menos colunas que o esperado."
                    );
                }

                NumeroInscricao numeroInscricao = new NumeroInscricao(colunas[0].trim());
                ProvaId provaId = new ProvaId(UUID.fromString(colunas[1].trim()));
                Map<Integer, MarcacaoOpticaCapturada> marcacoes = new LinkedHashMap<>();

                for (int i = 0; i < ordensEsperadasQuestoes.size(); i++) {
                    int ordem = ordensEsperadasQuestoes.get(i);
                    String token = colunas[2 + i];
                    marcacoes.put(ordem, converterTokenOptico(token, numLinha));
                }

                fichasLidas.add(new FichaOpticaLida(numeroInscricao, provaId, Map.copyOf(marcacoes)));
            }
        } catch (IOException e) {
            throw new RegraDeNegocioException("Erro de I/O ao processar stream de fichas opticas CSV.", e);
        }
        return List.copyOf(fichasLidas);
    }

    @Override
    public RelatorioParsingLote processarEmChunks(
        InputStream streamArquivo,
        int tamanhoChunk,
        Consumer<List<FichaCapturadaItem>> consumidorDeChunkValido
    ) {
        Objects.requireNonNull(streamArquivo, "InputStream do arquivo optico obrigatorio.");
        Objects.requireNonNull(consumidorDeChunkValido, "Consumidor de chunk obrigatorio.");
        if (tamanhoChunk <= 0) {
            throw new RegraDeNegocioException("Tamanho do chunk deve ser maior que zero.");
        }

        long totalLinhasLidas = 0;
        long totalFichasValidas = 0;
        List<FalhaLinhaImportacao> falhasIsoladas = new ArrayList<>();
        List<FichaCapturadaItem> bufferChunk = new ArrayList<>(tamanhoChunk);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamArquivo, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                if (linha.isBlank() || linha.trim().startsWith("#") || linha.toLowerCase().startsWith("inscricaoid")) {
                    continue;
                }
                totalLinhasLidas++;
                try {
                    FichaCapturadaItem item = parsearLinhaChaveValor(totalLinhasLidas, linha);
                    bufferChunk.add(item);
                    totalFichasValidas++;

                    if (bufferChunk.size() >= tamanhoChunk) {
                        consumidorDeChunkValido.accept(List.copyOf(bufferChunk));
                        bufferChunk.clear();
                    }
                } catch (Exception ex) {
                    falhasIsoladas.add(new FalhaLinhaImportacao(totalLinhasLidas, linha, ex.getMessage()));
                }
            }

            if (!bufferChunk.isEmpty()) {
                consumidorDeChunkValido.accept(List.copyOf(bufferChunk));
                bufferChunk.clear();
            }
        } catch (IOException e) {
            throw new RegraDeNegocioException("Falha de leitura no streaming do arquivo CSV de fichas.", e);
        }

        return new RelatorioParsingLote(totalLinhasLidas, totalFichasValidas, List.copyOf(falhasIsoladas));
    }

    private FichaCapturadaItem parsearLinhaChaveValor(long numeroLinha, String linha) {
        String[] partes = linha.split(";", -1);
        if (partes.length < 3) {
            throw new RegraDeNegocioException(
                "Formato CSV invalido na linha " + numeroLinha + ". Esperado: inscricaoId;provaId;questaoId:alternativa,..."
            );
        }

        InscricaoId inscricaoId = new InscricaoId(UUID.fromString(partes[0].trim()));
        ProvaId provaId = new ProvaId(UUID.fromString(partes[1].trim()));
        Map<QuestaoId, Alternativa> marcacoes = new LinkedHashMap<>();

        String[] paresQuestao = partes[2].split(",");
        for (String par : paresQuestao) {
            if (par.isBlank()) {
                continue;
            }
            String[] kv = par.split(":", -1);
            if (kv.length != 2) {
                throw new RegraDeNegocioException("Par questao:marcacao invalido na linha " + numeroLinha + ": " + par);
            }
            QuestaoId questaoId = new QuestaoId(UUID.fromString(kv[0].trim()));
            MarcacaoOpticaCapturada capturada = converterTokenOptico(kv[1], numeroLinha);
            marcacoes.put(questaoId, capturada.paraAlternativaApuravel());
        }

        if (marcacoes.isEmpty()) {
            throw new RegraDeNegocioException("Nenhuma marcacao encontrada na linha " + numeroLinha);
        }

        return new FichaCapturadaItem(numeroLinha, inscricaoId, provaId, Collections.unmodifiableMap(marcacoes));
    }

    private MarcacaoOpticaCapturada converterTokenOptico(String tokenBruto, long numeroLinha) {
        if (tokenBruto == null || tokenBruto.isBlank()) {
            return MarcacaoOpticaCapturada.emBranco();
        }
        String limpo = tokenBruto.trim().toUpperCase();
        if ("*".equals(limpo) || "DUPLA".equals(limpo)) {
            return MarcacaoOpticaCapturada.duplaMarcacao();
        }
        if ("-".equals(limpo) || "BRANCO".equals(limpo)) {
            return MarcacaoOpticaCapturada.emBranco();
        }
        try {
            return MarcacaoOpticaCapturada.valida(Alternativa.valueOf(limpo));
        } catch (IllegalArgumentException ex) {
            throw new RegraDeNegocioException(
                "Simbolo optico invalido '" + tokenBruto + "' na linha " + numeroLinha
            );
        }
    }
}
