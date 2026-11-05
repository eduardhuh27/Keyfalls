package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.ResultadoId;

import java.util.List;
import java.util.Optional;

public interface ResultadoRepository {
    void salvar(Resultado resultado);
    void salvarTodos(List<Resultado> resultados);
    Optional<Resultado> buscarPorId(ResultadoId id);
    Optional<Resultado> buscarPorInscricao(InscricaoId inscricaoId);
    List<Resultado> listarPorConcursoECargo(ConcursoId concursoId, CargoId cargoId);
}
