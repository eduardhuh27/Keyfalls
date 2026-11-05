package br.com.keyfalls.apuracao.application;

import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.FormatoArquivoOptico;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Porta (Adapter Pattern) para leitura agnostica do arquivo bruto da central de captura optica,
 * suportando multiplos layouts fisicos (CSV delimitado ou TXT posicional CNAB/Scantron)
 * e tratando explicitamente DUPLA_MARCACAO e EM_BRANCO.
 */
public interface FichaRespostaParserPort {

    enum StatusMarcacaoOptica {
        VALIDA,
        EM_BRANCO,
        DUPLA_MARCACAO
    }

    record MarcacaoOpticaCapturada(StatusMarcacaoOptica status, Alternativa alternativa) {
        public MarcacaoOpticaCapturada {
            Objects.requireNonNull(status, "Status da marcacao optica obrigatorio.");
            if (status == StatusMarcacaoOptica.VALIDA && alternativa == null) {
                throw new RegraDeNegocioException("Alternativa obrigatoria quando a marcacao e VALIDA.");
            }
            if (status != StatusMarcacaoOptica.VALIDA && alternativa != null) {
                throw new RegraDeNegocioException("Marcacao EM_BRANCO ou DUPLA_MARCACAO nao pode ter alternativa valida.");
            }
        }

        public static MarcacaoOpticaCapturada valida(Alternativa alternativa) {
            return new MarcacaoOpticaCapturada(StatusMarcacaoOptica.VALIDA, alternativa);
        }

        public static MarcacaoOpticaCapturada emBranco() {
            return new MarcacaoOpticaCapturada(StatusMarcacaoOptica.EM_BRANCO, null);
        }

        public static MarcacaoOpticaCapturada duplaMarcacao() {
            return new MarcacaoOpticaCapturada(StatusMarcacaoOptica.DUPLA_MARCACAO, null);
        }

        public Alternativa paraAlternativaApuravel() {
            // Tanto EM_BRANCO quanto DUPLA_MARCACAO resultam em ausencia de marcacao pontuavel (null)
            return status == StatusMarcacaoOptica.VALIDA ? alternativa : null;
        }
    }

    record FichaOpticaLida(
        NumeroInscricao numeroInscricao,
        ProvaId provaId,
        Map<Integer, MarcacaoOpticaCapturada> marcacoesPorOrdemQuestao
    ) {}

    boolean suportaFormato(FormatoArquivoOptico formato);

    List<FichaOpticaLida> lerStream(InputStream streamArquivo, List<Integer> ordensEsperadasQuestoes);
}
