package br.com.keyfalls.logistica.application;

import br.com.keyfalls.logistica.domain.AlocadorDeSalas.CandidatoHomologadoParaAlocacao;
import br.com.keyfalls.shared.domain.ConcursoId;

import java.util.List;

/**
 * Secondary Output Port (ACL) da camada application de Logistica para consultar
 * as inscricoes homologadas (PAGAS ou ISENTAS) de um concurso que ainda nao possuem sala alocada.
 */
public interface ConsultaCandidatosHomologadosPort {

    List<CandidatoHomologadoParaAlocacao> listarHomologadosPendentesDeAlocacao(ConcursoId concursoId);
}
