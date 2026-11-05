package br.com.keyfalls.logistica.interfaces.rest;

import br.com.keyfalls.logistica.application.LogisticaUseCases;
import br.com.keyfalls.logistica.interfaces.rest.dto.LogisticaDtos.AlocacaoExecutadaResponse;
import br.com.keyfalls.logistica.interfaces.rest.dto.LogisticaDtos.CadastrarLocalProvaRequest;
import br.com.keyfalls.logistica.interfaces.rest.dto.LogisticaDtos.ListaPresencaResponse;
import br.com.keyfalls.logistica.interfaces.rest.dto.LogisticaDtos.LocalProvaResponse;
import br.com.keyfalls.logistica.interfaces.rest.dto.LogisticaDtos.RegistrarPresencasSalaRequest;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
@Tag(name = "3. Logistica de Prova", description = "Locais de prova, salas homogeneas por cargo, alocacao por proximidade e lista de presenca")
@SecurityRequirement(name = "bearerJwt")
public class LogisticaController {

    private final LogisticaUseCases logisticaUseCases;

    public LogisticaController(LogisticaUseCases logisticaUseCases) {
        this.logisticaUseCases = Objects.requireNonNull(logisticaUseCases);
    }

    @PostMapping("/concursos/{id}/locais")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Credencia um local de prova com suas salas (vinculadas a cargos) e fiscais")
    public ResponseEntity<LocalProvaResponse> cadastrarLocal(
        @PathVariable("id") UUID concursoId,
        @Valid @RequestBody CadastrarLocalProvaRequest request
    ) {
        var out = logisticaUseCases.cadastrarLocalSalasEFiscais(request.toCommand(concursoId));
        return ResponseEntity
            .created(URI.create("/concursos/" + concursoId + "/locais/" + out.localDeProvaId()))
            .body(LocalProvaResponse.from(out));
    }

    @PostMapping("/concursos/{id}/alocacoes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Executa o AlocadorDeSalas distribuindo candidatos homologados por proximidade/bairro")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Alocacao em lote processada"),
        @ApiResponse(responseCode = "409", description = "Capacidade maxima das salas excedida para o cargo"),
        @ApiResponse(responseCode = "422", description = "Incompatibilidade de cargo na sala")
    })
    public ResponseEntity<AlocacaoExecutadaResponse> executarAlocacoes(@PathVariable("id") UUID concursoId) {
        var out = logisticaUseCases.executarAlocacaoPorProximidade(concursoId);
        return ResponseEntity.accepted().body(AlocacaoExecutadaResponse.from(out));
    }

    @PostMapping("/salas/{id}/presencas")
    @PreAuthorize("hasAnyRole('FISCAL', 'ADMIN')")
    @Operation(summary = "Registra a lista de presenca assinada pelos candidatos na sala")
    public ResponseEntity<ListaPresencaResponse> registrarPresencas(
        @PathVariable("id") UUID salaId,
        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody RegistrarPresencasSalaRequest request
    ) {
        UUID operadorId = UUID.fromString(jwt.getSubject());
        var out = logisticaUseCases.registrarListaDePresenca(request.toCommand(salaId, operadorId));
        return ResponseEntity.ok(ListaPresencaResponse.from(out));
    }
}
