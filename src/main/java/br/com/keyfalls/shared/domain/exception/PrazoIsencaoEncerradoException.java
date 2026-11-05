package br.com.keyfalls.shared.domain.exception;

import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.io.Serial;

public final class PrazoIsencaoEncerradoException extends RegraDeNegocioException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PrazoIsencaoEncerradoException(String mensagem) {
        super(mensagem);
    }
}
