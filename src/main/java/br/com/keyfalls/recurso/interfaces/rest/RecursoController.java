package br.com.keyfalls.recurso.interfaces.rest;

import br.com.keyfalls.recurso.application.RecursoUseCases;
import br.com.keyfalls.recurso.interfaces.rest.dto.RecursoDtos.AbrirRecursoRequest;
import br.com.keyfalls.recurso.interfaces.rest.dto.RecursoDtos.JulgarRecursoRequest;
import br.com.keyfalls.recurso.interfaces.rest.dto.RecursoDtos.RecursoJulgadoResponse;
import br.com.keyfalls.recurso.interfaces.rest.dto.RecursoDtos.RecursoProtocoladoResponse;
import br.com.keyfalls.recurso.interfaces.rest.dto.RecursoDtos.ResultadoFinalResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
@Tag(name = "5. Recursos e Homologacao Final", description = "Interposicao de recursos contra questoes, julgamento da banca e homologacao do certame")
@SecurityRequirement(name = "bearerJwt")
public class RecursoController {

    private final RecursoUseCases recursoUseCases;

    public RecursoController(RecursoUseCases recursoUseCases) {
        this.recursoUseCases = Objects.requireNonNull(recursoUseCases);
    }

    @PostMapping("/recursos")
    @PreAuthorize("hasRole('CANDIDATO')")
    @Operation(summary = "Candidato autenticado protocola recurso fundamentado contra uma questao")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Recurso protocolado com sucesso"),
        @ApiResponse(responseCode = "422", description = "Fundamentacao insuficiente ou fora da janela recursal")
    })
    public ResponseEntity<RecursoProtocoladoResponse> abrirRecurso(
        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody AbrirRecursoRequest request
    ) {
        UUID candidatoId = UUID.fromString(jwt.getSubject());
        var out = recursoUseCases.abrirRecurso(request.toCommand(candidatoId));
        return ResponseEntity
            .created(URI.create("/recursos/" + out.recursoId()))
            .body(RecursoProtocoladoResponse.from(out));
    }

    @PutMapping("/recursos/{id}/julgamento")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Banca/Admin julga o recurso; se anular questao, dispara QuestaoAnulada para recalculo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recurso julgado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Recurso nao encontrado"),
        @ApiResponse(responseCode = "409", description = "Recurso ja julgado anteriormente")
    })
    public ResponseEntity<RecursoJulgadoResponse> julgarRecurso(
        @PathVariable("id") UUID recursoId,
        @Valid @RequestBody JulgarRecursoRequest request
    ) {
        var out = recursoUseCases.julgarRecurso(request.toCommand(recursoId));
        return ResponseEntity.ok(RecursoJulgadoResponse.from(out));
    }

    @PostMapping("/concursos/{id}/resultado-final")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Homologa e trava o resultado final apos o julgamento de todos os recursos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultado final homologado e publicado"),
        @ApiResponse(responseCode = "409", description = "Ainda existem recursos pendentes de julgamento (RecursosPendentesException)")
    })
    public ResponseEntity<ResultadoFinalResponse> homologarResultadoFinal(@PathVariable("id") UUID concursoId) {
        var out = recursoUseCases.homologarResultadoFinal(concursoId);
        return ResponseEntity.ok(ResultadoFinalResponse.from(out));
    }
}
