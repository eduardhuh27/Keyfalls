package br.com.keyfalls.apuracao.infrastructure.parser;

import br.com.keyfalls.apuracao.application.FichaRespostaParserPort;
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
 * Adapter 2: Layout TXT Posicional de Largura Fixa (estilo leitoras opticas legadas / CNAB).
 * Posicoes:
 *   [00..10) -> Numero de Inscricao (10 digitos)
 *   [10..46) -> UUID da Prova (36 caracteres)
 *   [46..46+N) -> String continua de marcacoes (1 caractere por questao: ' ' = EM_BRANCO, '*' = DUPLA_MARCACAO, 'A'..'E' = VALIDA)
 */
@Component
public class PosicionalTxtFichaParser implements FichaRespostaParserPort {

    private static final int OFFSET_PROVA_UUID = 10;
    private static final int OFFSET_INICIO_RESPOSTAS = 46;

    @Override
    public boolean suportaFormato(FormatoArquivoOptico formato) {
        return formato == FormatoArquivoOptico.POSICIONAL_TXT_LARGURA_FIXA;
    }

    @Override
    public List<FichaOpticaLida> lerStream(InputStream streamArquivo, List<Integer> ordensEsperadasQuestoes) {
        List<FichaOpticaLida> resultado = new ArrayList<>();
        int tamanhoEsperadoLinha = OFFSET_INICIO_RESPOSTAS + ordensEsperadasQuestoes.size();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(streamArquivo, StandardCharsets.ISO_8859_1))) {
            String linha;
            long numLinha = 0;
            while ((linha = reader.readLine()) != null) {
                numLinha++;
                if (linha.isEmpty()) continue;
                if (linha.length() != tamanhoEsperadoLinha) {
                    throw new RegraDeNegocioException(
                        "Linha posicional %d invalida: esperado comprimento fixo %d, recebido %d."
                            .formatted(numLinha, tamanhoEsperadoLinha, linha.length())
                    );
                }

                NumeroInscricao numeroInscricao = new NumeroInscricao(linha.substring(0, OFFSET_PROVA_UUID).trim());
                ProvaId provaId = new ProvaId(UUID.fromString(linha.substring(OFFSET_PROVA_UUID, OFFSET_INICIO_RESPOSTAS)));
                String blocoRespostas = linha.substring(OFFSET_INICIO_RESPOSTAS);

                Map<Integer, MarcacaoOpticaCapturada> mapa = new LinkedHashMap<>();
                for (int i = 0; i < ordensEsperadasQuestoes.size(); i++) {
                    String simbolo = String.valueOf(blocoRespostas.charAt(i)).trim();
                    mapa.put(ordensEsperadasQuestoes.get(i), CsvFichaParser.interpretarSimbolo(simbolo, numLinha));
                }
                resultado.add(new FichaOpticaLida(numeroInscricao, provaId, Map.copyOf(mapa)));
            }
        } catch (IOException ex) {
            throw new RegraDeNegocioException("Erro de leitura no parser TXT posicional: " + ex.getMessage());
        }
        return List.copyOf(resultado);
    }
}
