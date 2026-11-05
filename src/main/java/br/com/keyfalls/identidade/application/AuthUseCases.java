package br.com.keyfalls.identidade.application;

import br.com.keyfalls.shared.domain.security.PerfilAcesso;

import java.util.Set;
import java.util.UUID;

public interface AuthUseCases {

    record RegistrarCandidatoCommand(
        String nomeCompleto,
        String cpf,
        String email,
        String senhaPura,
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep,
        Double latitude,
        Double longitude
    ) {}

    record CandidatoRegistradoOutput(
        UUID candidatoId,
        String nomeCompleto,
        String cpfFormatado,
        String email,
        String accessToken
    ) {}

    record LoginCommand(
        String loginCpfOuEmail,
        String senhaPura
    ) {}

    record TokenAutenticacaoOutput(
        UUID usuarioId,
        String email,
        Set<String> roles,
        String accessToken
    ) {}

    interface HashSenhaPort {
        String gerarHash(String senhaPura);
        boolean verificar(String senhaPura, String senhaHash);
    }

    interface EmissorTokenJwtPort {
        String emitirToken(UUID usuarioId, String email, Set<PerfilAcesso> perfis);
    }

    CandidatoRegistradoOutput registrarCandidato(RegistrarCandidatoCommand command);

    TokenAutenticacaoOutput autenticar(LoginCommand command);

    record EmitirTokenPerfilCommand(
        String email,
        PerfilAcesso perfil
    ) {}

    TokenAutenticacaoOutput emitirTokenPorPerfil(EmitirTokenPerfilCommand command);
}
