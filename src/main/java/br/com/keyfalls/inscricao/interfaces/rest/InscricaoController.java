package br.com.keyfalls.inscricao.interfaces.rest;

import br.com.keyfalls.inscricao.application.InscricaoConsultasEIsencaoUseCases;
import br.com.keyfalls.inscricao.application.InscricaoConsultasEIsencaoUseCases.SolicitarIsencaoCommand;
import br.com.keyfalls.inscricao.application.InscricaoRealizadaOutput;
import br.com.keyfalls.inscricao.application.RealizarInscricaoUseCase;
import br.com.keyfalls.inscricao.interfaces.rest.dto.InscricaoIsencaoDtos.BoletoResponse;
import br.com.keyfalls.inscricao.interfaces.rest.dto.InscricaoIsencaoDtos.IsencaoResponse;
import br.com.keyfalls.inscricao.interfaces.rest.dto.InscricaoIsencaoDtos.SolicitarIsencaoRequest;
import br.com.keyfalls.inscricao.interfaces.rest.dto.InscricaoResponse;
import br.com.keyfalls.inscricao.interfaces.rest.dto.RealizarInscricaoRequest;
import br.com.keyfalls.inscricao.interfaces.rest.mapper.InscricaoApiMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@Tag(name = "2. Inscricoes, Boletos e Isencoes", description = "Inscricao de candidatos, cobranca bancaria e pedidos de isencao")
@SecurityRequirement(name = "bearerJwt")
public class InscricaoController {

    private final RealizarInscricaoUseCase realizarInscricaoUseCase;
    private final InscricaoConsultasEIsencaoUseCases consultasEIsencaoUseCases;
    private final InscricaoApiMapper apiMapper;

    public InscricaoController(
        RealizarInscricaoUseCase realizarInscricaoUseCase,
        InscricaoConsultasEIsencaoUseCases consultasEIsencaoUseCases,
        InscricaoApiMapper apiMapper
    ) {
        this.realizarInscricaoUseCase = Objects.requireNonNull(realizarInscricaoUseCase);
        this.consultasEIsencaoUseCases = Objects.requireNonNull(consultasEIsencaoUseCases);
        this.apiMapper = Objects.requireNonNull(apiMapper);
    }

    @PostMapping("/inscricoes")
    @PreAuthorize("hasRole('CANDIDATO')")
    @Operation(summary = "Realiza a inscricao do candidato autenticado (anti-IDOR via JWT sub)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Inscricao realizada com sucesso"),
        @ApiResponse(responseCode = "409", description = "Candidato ja inscrito no concurso"),
        @ApiResponse(responseCode = "422", description = "Fora do periodo de inscricao")
    })
    public ResponseEntity<InscricaoResponse> realizar(
        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody RealizarInscricaoRequest request
    ) {
        UUID candidatoAutenticadoId = UUID.fromString(jwt.getSubject());
        InscricaoRealizadaOutput output = realizarInscricaoUseCase.executar(
            apiMapper.toCommand(candidatoAutenticadoId, request)
        );
        return ResponseEntity
            .created(URI.create("/inscricoes/" + output.inscricaoId()))
            .body(apiMapper.toResponse(output));
    }

    @GetMapping("/inscricoes/{id}/boleto")
    @PreAuthorize("hasAnyRole('CANDIDATO', 'ADMIN')")
    @Operation(summary = "Consulta os dados e linha digitavel do boleto da inscricao")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Boleto retornado com sucesso"),
        @ApiResponse(responseCode = "403", description = "Tentativa de acesso a boleto de outro candidato (anti-IDOR)"),
        @ApiResponse(responseCode = "404", description = "Inscricao ou boleto nao encontrado")
    })
    public ResponseEntity<BoletoResponse> consultarBoleto(
        @PathVariable("id") UUID inscricaoId,
        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        UUID solicitanteId = UUID.fromString(jwt.getSubject());
        var out = consultasEIsencaoUseCases.consultarBoleto(inscricaoId, solicitanteId, possuiRoleAdmin(jwt));
        return ResponseEntity.ok(BoletoResponse.from(out));
    }

    @PostMapping("/inscricoes/{id}/isencao")
    @PreAuthorize("hasRole('CANDIDATO')")
    @Operation(summary = "Solicita isencao de taxa dentro do prazo estrito do edital")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pedido de isencao registrado"),
        @ApiResponse(responseCode = "403", description = "Inscricao nao pertence ao candidato autenticado"),
        @ApiResponse(responseCode = "422", description = "Prazo de isencao encerrado")
    })
    public ResponseEntity<IsencaoResponse> solicitarIsencao(
        @PathVariable("id") UUID inscricaoId,
        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody SolicitarIsencaoRequest request
    ) {
        UUID candidatoId = UUID.fromString(jwt.getSubject());
        var out = consultasEIsencaoUseCases.solicitarIsencao(
            new SolicitarIsencaoCommand(inscricaoId, candidatoId, request.justificativaLegal())
        );
        return ResponseEntity
            .created(URI.create("/isencoes/" + out.isencaoId()))
            .body(IsencaoResponse.from(out));
    }

    @GetMapping("/isencoes/{id}")
    @PreAuthorize("hasAnyRole('CANDIDATO', 'ADMIN')")
    @Operation(summary = "Consulta o status e o parecer do pedido de isencao")
    public ResponseEntity<IsencaoResponse> consultarIsencao(
        @PathVariable("id") UUID isencaoId,
        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        UUID solicitanteId = UUID.fromString(jwt.getSubject());
        var out = consultasEIsencaoUseCases.consultarIsencao(isencaoId, solicitanteId, possuiRoleAdmin(jwt));
        return ResponseEntity.ok(IsencaoResponse.from(out));
    }

    private static boolean possuiRoleAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ROLE_ADMIN");
    }
}
