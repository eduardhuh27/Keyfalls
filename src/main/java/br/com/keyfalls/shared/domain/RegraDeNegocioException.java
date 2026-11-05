package br.com.keyfalls.shared.domain;

import java.io.Serial;

public class RegraDeNegocioException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
