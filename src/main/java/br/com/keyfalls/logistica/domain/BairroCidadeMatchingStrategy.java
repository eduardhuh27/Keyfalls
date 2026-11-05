package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.Endereco;

import java.util.Comparator;
import java.util.Objects;

/**
 * Ordena locais de prova priorizando:
 * 1) Mesmo bairro e mesma cidade;
 * 2) Mesma cidade e menor diferenca de prefixo de CEP;
 * 3) Mesma UF.
 */
public final class BairroCidadeMatchingStrategy implements EstrategiaProximidadeAlocacao {

    @Override
    public Comparator<LocalDeProva> comparadorDeProximidadePara(Endereco enderecoCandidato) {
        Objects.requireNonNull(enderecoCandidato, "Endereco do candidato obrigatorio.");
        return Comparator
            .comparing((LocalDeProva local) -> !enderecoCandidato.mesmoBairroECidade(local.endereco()))
            .thenComparing(local -> !enderecoCandidato.mesmaCidade(local.endereco()))
            .thenComparing(local -> !enderecoCandidato.uf().equalsIgnoreCase(local.endereco().uf()))
            .thenComparingInt(local -> diferencaPrefixoCep(enderecoCandidato, local.endereco()));
    }

    private static int diferencaPrefixoCep(Endereco origem, Endereco destino) {
        int cepA = Integer.parseInt(origem.cep().substring(0, 5));
        int cepB = Integer.parseInt(destino.cep().substring(0, 5));
        return Math.abs(cepA - cepB);
    }
}
