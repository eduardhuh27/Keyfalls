package br.com.keyfalls.identidade.interfaces.rest;

import br.com.keyfalls.identidade.application.AuthUseCases;
import br.com.keyfalls.identidade.application.AuthUseCases.CandidatoRegistradoOutput;
import br.com.keyfalls.identidade.application.AuthUseCases.EmitirTokenPerfilCommand;
import br.com.keyfalls.identidade.application.AuthUseCases.LoginCommand;
import br.com.keyfalls.identidade.application.AuthUseCases.RegistrarCandidatoCommand;
import br.com.keyfalls.identidade.application.AuthUseCases.TokenAutenticacaoOutput;
import br.com.keyfalls.shared.domain.security.PerfilAcesso;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RestController
@Tag(name = "0. Identidade e Autenticacao JWT", description = "Registro de candidatos e emissao de tokens Bearer JWT")
public class AuthController {

    private final AuthUseCases authUseCases;

    public AuthController(AuthUseCases authUseCases) {
        this.authUseCases = Objects.requireNonNull(authUseCases);
    }

    public record RegistrarCandidatoRequest(
        @NotBlank(message = "Nome completo obrigatorio.")
        String nomeCompleto,
        @NotBlank(message = "CPF obrigatorio.")
        String cpf,
        @NotBlank(message = "E-mail obrigatorio.")
        @Email(message = "E-mail invalido.")
        String email,
        @NotBlank(message = "Senha obrigatoria.")
        @Size(min = 8, message = "A senha deve conter ao menos 8 caracteres.")
        String senha,
        @NotBlank(message = "Logradouro obrigatorio.")
        String logradouro,
        @NotBlank(message = "Numero obrigatorio.")
        String numero,
        @NotBlank(message = "Bairro obrigatorio.")
        String bairro,
        @NotBlank(message = "Cidade obrigatoria.")
        String cidade,
        @NotBlank(message = "UF obrigatoria.")
        @Size(min = 2, max = 2, message = "UF deve possuir 2 letras.")
        String uf,
        @NotBlank(message = "CEP obrigatorio.")
        String cep,
        Double latitude,
        Double longitude
    ) {
        public RegistrarCandidatoCommand toCommand() {
            return new RegistrarCandidatoCommand(
                nomeCompleto, cpf, email, senha,
                logradouro, numero, bairro, cidade, uf, cep, latitude, longitude
            );
        }
    }

    public record CandidatoRegistradoResponse(
        UUID candidatoId,
        String nomeCompleto,
        String cpf,
        String email,
        String accessToken
    ) {
        public static CandidatoRegistradoResponse from(CandidatoRegistradoOutput out) {
            return new CandidatoRegistradoResponse(
                out.candidatoId(), out.nomeCompleto(), out.cpfFormatado(), out.email(), out.accessToken()
            );
        }
    }

    public record LoginRequest(
        @NotBlank(message = "CPF ou e-mail obrigatorio.")
        String login,
        @NotBlank(message = "Senha obrigatoria.")
        String senha
    ) {
        public LoginCommand toCommand() {
            return new LoginCommand(login, senha);
        }
    }

    public record EmitirTokenRequest(
        String email,
        PerfilAcesso role
    ) {
        public EmitirTokenPerfilCommand toCommand() {
            return new EmitirTokenPerfilCommand(email, role);
        }
    }

    public record TokenResponse(
        UUID usuarioId,
        String email,
        Set<String> roles,
        String accessToken
    ) {
        public static TokenResponse from(TokenAutenticacaoOutput out) {
            return new TokenResponse(out.usuarioId(), out.email(), out.roles(), out.accessToken());
        }
    }

    @PostMapping({"/auth/registrar", "/candidatos"})
    @Operation(summary = "Registra um novo candidato validando CPF/Endereco e retorna token JWT assinado")
    public ResponseEntity<CandidatoRegistradoResponse> registrar(
        @Valid @RequestBody RegistrarCandidatoRequest request
    ) {
        var out = authUseCases.registrarCandidato(request.toCommand());
        return ResponseEntity
            .created(URI.create("/candidatos/" + out.candidatoId()))
            .body(CandidatoRegistradoResponse.from(out));
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Autentica candidato/operador e emite Bearer JWT com claim 'sub' e 'roles'")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var out = authUseCases.autenticar(request.toCommand());
        return ResponseEntity.ok(TokenResponse.from(out));
    }

    @PostMapping("/auth/token")
    @Operation(summary = "Emite token JWT diretamente por perfil (ROLE_ADMIN, ROLE_FISCAL, ROLE_CANDIDATO)")
    public ResponseEntity<TokenResponse> emitirToken(@RequestBody(required = false) EmitirTokenRequest request) {
        var cmd = request != null ? request.toCommand() : new EmitirTokenPerfilCommand(null, PerfilAcesso.ROLE_ADMIN);
        var out = authUseCases.emitirTokenPorPerfil(cmd);
        return ResponseEntity.ok(TokenResponse.from(out));
    }
}
