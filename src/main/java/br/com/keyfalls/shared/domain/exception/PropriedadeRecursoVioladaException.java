package br.com.keyfalls.shared.domain.exception;

import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.io.Serial;

public final class PropriedadeRecursoVioladaException extends RegraDeNegocioException {
    @Serial
    private static final long serialVersionUID = 1L;

    public PropriedadeRecursoVioladaException(String mensagem) {
        super(mensagem);
    }
}
