package br.com.keyfalls.comunicacao.domain;

import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.util.Objects;

public record MensagemTransacional(
    Email destinatario,
    String assunto,
    String corpo
) {
    public MensagemTransacional {
        Objects.requireNonNull(destinatario, "E-mail do destinatario obrigatorio.");
        if (assunto == null || assunto.isBlank()) {
            throw new RegraDeNegocioException("Assunto da mensagem transacional obrigatorio.");
        }
        if (corpo == null || corpo.isBlank()) {
            throw new RegraDeNegocioException("Corpo da mensagem transacional obrigatorio.");
        }
        assunto = assunto.trim();
        corpo = corpo.trim();
    }
}
