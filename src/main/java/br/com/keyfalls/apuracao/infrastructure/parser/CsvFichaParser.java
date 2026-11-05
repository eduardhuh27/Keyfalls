package br.com.keyfalls.apuracao.infrastructure.parser;

import br.com.keyfalls.apuracao.application.FichaRespostaParserPort;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.FormatoArquivoOptico;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.ProvaId;
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

/**
 * Adapter 1: Layout CSV delimitado por ponto-e-virgula (;).
 * Exemplo de linha: 2026000001;550e8400-e29b-41d4-a716-446655440000;A;C;*; ;E
 * Convenção: '*' = DUPLA_MARCACAO, ''/' ' = EM_BRANCO, 'A'..'E' = VALIDA.
 */
@Component
public class CsvFichaParser implements FichaRespostaParserPort {

    @Override
    public boolean suportaFormato(FormatoArquivoOptico formato) {
        return formato == FormatoArquivoOptico.CSV_PONTO_E_VIRGULA;
    }

    @Override
    public List<FichaOpticaLida> lerStream(InputStream streamArquivo, List<Integer> ordensEsperadasQuestoes) {
        List<FichaOpticaLida> resultado = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamArquivo, StandardCharsets.UTF_8))) {
            String linha;
            long numLinha = 0;
            while ((linha = reader.readLine()) != null) {
                numLinha++;
                if (linha.isBlank()) continue;

                String[] tokens = linha.split(";", -1);
                if (tokens.length != 2 + ordensEsperadasQuestoes.size()) {
                    throw new RegraDeNegocioException(
                        "Linha CSV " + numLinha + " possui quantidade divergente de colunas de questoes."
                    );
                }

                NumeroInscricao numeroInscricao = new NumeroInscricao(tokens[0].trim());
                ProvaId provaId = new ProvaId(UUID.fromString(tokens[1].trim()));
                Map<Integer, MarcacaoOpticaCapturada> mapa = new LinkedHashMap<>();

                for (int i = 0; i < ordensEsperadasQuestoes.size(); i++) {
                    String simbolo = tokens[i + 2].trim();
                    mapa.put(ordensEsperadasQuestoes.get(i), interpretarSimbolo(simbolo, numLinha));
                }
                resultado.add(new FichaOpticaLida(numeroInscricao, provaId, Map.copyOf(mapa)));
            }
        } catch (IOException ex) {
            throw new RegraDeNegocioException("Erro de leitura no parser CSV optico: " + ex.getMessage());
        }
        return List.copyOf(resultado);
    }

    static MarcacaoOpticaCapturada interpretarSimbolo(String simbolo, long numLinha) {
        if (simbolo.isEmpty() || "-".equals(simbolo)) {
            return MarcacaoOpticaCapturada.emBranco();
        }
        if ("*".equals(simbolo)) {
            return MarcacaoOpticaCapturada.duplaMarcacao();
        }
        try {
            return MarcacaoOpticaCapturada.valida(Alternativa.valueOf(simbolo.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("Simbolo optico invalido '" + simbolo + "' na linha " + numLinha);
        }
    }
}
