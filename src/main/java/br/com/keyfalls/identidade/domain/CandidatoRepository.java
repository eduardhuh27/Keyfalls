package br.com.keyfalls.identidade.domain;

import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.Email;

import java.util.Optional;

public interface CandidatoRepository {
    void salvar(Candidato candidato);
    Optional<Candidato> buscarPorId(CandidatoId id);
    Optional<Candidato> buscarPorCpf(CPF cpf);
    Optional<Candidato> buscarPorEmail(Email email);
}
