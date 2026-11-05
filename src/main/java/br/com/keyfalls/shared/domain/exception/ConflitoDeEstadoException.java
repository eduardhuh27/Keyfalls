package br.com.keyfalls.shared.domain.exception;

import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.io.Serial;

public final class ConflitoDeEstadoException extends RegraDeNegocioException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ConflitoDeEstadoException(String mensagem) {
        super(mensagem);
    }
}
