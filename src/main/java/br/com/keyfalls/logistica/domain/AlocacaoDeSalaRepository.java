package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.AlocacaoDeSalaId;
import br.com.keyfalls.shared.domain.InscricaoId;

import java.util.List;
import java.util.Optional;

public interface AlocacaoDeSalaRepository {
    void salvar(AlocacaoDeSala alocacao);
    void salvarTodas(List<AlocacaoDeSala> alocacoes);
    Optional<AlocacaoDeSala> buscarPorId(AlocacaoDeSalaId id);
    Optional<AlocacaoDeSala> buscarPorInscricao(InscricaoId inscricaoId);
}
