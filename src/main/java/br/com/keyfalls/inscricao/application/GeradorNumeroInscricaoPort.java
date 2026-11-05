package br.com.keyfalls.inscricao.application;

import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;

public interface GeradorNumeroInscricaoPort {
    NumeroInscricao proximoNumero(ConcursoId concursoId);
}
