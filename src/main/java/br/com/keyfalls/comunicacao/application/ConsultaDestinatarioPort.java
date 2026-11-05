package br.com.keyfalls.comunicacao.application;

import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.SalaId;

import java.util.List;

/**
 * Porta de Anti-Corruption Layer (ACL) do contexto Comunicacao para resolver dados
 * de contato sem acoplamento direto as entidades de Identidade, Inscricao ou Logistica.
 */
public interface ConsultaDestinatarioPort {

    record DadosContatoCandidato(String nomeCompleto, Email email) {}

    record DadosLocalAlocado(String nomeInstituicao, String identificacaoSala, String enderecoFormatado) {}

    DadosContatoCandidato buscarPorCandidatoId(CandidatoId candidatoId);

    DadosContatoCandidato buscarPorInscricaoId(InscricaoId inscricaoId);

    DadosLocalAlocado buscarDetalhesSala(LocalDeProvaId localDeProvaId, SalaId salaId);

    List<DadosContatoCandidato> listarCandidatosHomologadosDoCargo(ConcursoId concursoId, CargoId cargoId);
}
