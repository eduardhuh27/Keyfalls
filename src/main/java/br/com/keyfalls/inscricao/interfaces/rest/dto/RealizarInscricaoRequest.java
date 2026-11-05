package br.com.keyfalls.inscricao.interfaces.rest.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Observe a protecao anti-IDOR: o candidatoId NAO faz parte do JSON de entrada;
 * ele e extraido exclusivamente da claim 'sub' do token JWT autenticado.
 */
public record RealizarInscricaoRequest(
    @NotNull(message = "O identificador do concurso e obrigatorio.")
    UUID concursoId,

    @NotNull(message = "O identificador do cargo e obrigatorio.")
    UUID cargoId
) {}
