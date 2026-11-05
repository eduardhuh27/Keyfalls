package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.time.Instant;
import java.util.Objects;

public record ListaDePresenca(
    boolean presente,
    String hashAssinaturaCandidato,
    String documentoApresentado,
    Instant registradoEm
) {

    public ListaDePresenca {
        Objects.requireNonNull(registradoEm, "Data/hora do registro de presenca obrigatoria.");
        if (presente) {
            if (hashAssinaturaCandidato == null || hashAssinaturaCandidato.isBlank()) {
                throw new RegraDeNegocioException("Assinatura na lista de presenca obrigatoria para candidato presente.");
            }
            if (documentoApresentado == null || documentoApresentado.isBlank()) {
                throw new RegraDeNegocioException("Documento de identificacao obrigatorio para candidato presente.");
            }
            hashAssinaturaCandidato = hashAssinaturaCandidato.trim();
            documentoApresentado = documentoApresentado.trim();
        }
    }

    public static ListaDePresenca assinada(String hashAssinatura, String documentoApresentado, Instant horario) {
        return new ListaDePresenca(true, hashAssinatura, documentoApresentado, horario);
    }

    public static ListaDePresenca ausente(Instant horarioFechamentoPortoes) {
        return new ListaDePresenca(false, null, null, horarioFechamentoPortoes);
    }
}
