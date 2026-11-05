package br.com.keyfalls.concurso.interfaces.rest;

import br.com.keyfalls.concurso.application.ConcursoUseCases;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.AdicionarCargoRequest;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.CadastrarQuestoesEmLoteRequest;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.CargoCriadoResponse;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.ConcursoResponse;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.CriarConcursoRequest;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.GabaritoPublicadoResponse;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.PublicarGabaritoRequest;
import br.com.keyfalls.concurso.interfaces.rest.dto.ConcursoDtos.QuestoesCadastradasResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

@RestController
@Tag(name = "1. Concursos, Cargos, Provas e Gabaritos", description = "Gestao normativa do edital pela banca organizadora")
@SecurityRequirement(name = "bearerJwt")
public class ConcursoController {

    private final ConcursoUseCases concursoUseCases;

    public ConcursoController(ConcursoUseCases concursoUseCases) {
        this.concursoUseCases = Objects.requireNonNull(concursoUseCases);
    }

    @PostMapping("/concursos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria um novo concurso publico com janelas de inscricao e isencao")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Concurso criado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Payload invalido"),
        @ApiResponse(responseCode = "422", description = "Regra de negocio editalicia violada")
    })
    public ResponseEntity<ConcursoResponse> criarConcurso(@Valid @RequestBody CriarConcursoRequest request) {
        var out = concursoUseCases.criarConcurso(request.toCommand());
        return ResponseEntity
            .created(URI.create("/concursos/" + out.concursoId()))
            .body(ConcursoResponse.from(out));
    }

    @PostMapping("/concursos/{id}/cargos")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Adiciona um cargo e suas respectivas provas ponderadas ao concurso")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Cargo e provas adicionados com sucesso"),
        @ApiResponse(responseCode = "404", description = "Concurso nao encontrado")
    })
    public ResponseEntity<CargoCriadoResponse> adicionarCargo(
        @PathVariable("id") UUID concursoId,
        @Valid @RequestBody AdicionarCargoRequest request
    ) {
        var out = concursoUseCases.adicionarCargoEProvas(request.toCommand(concursoId));
        return ResponseEntity
            .created(URI.create("/concursos/" + concursoId + "/cargos/" + out.cargoId()))
            .body(CargoCriadoResponse.from(out));
    }

    @PostMapping("/provas/{id}/questoes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cadastra questoes objetivas em uma prova")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Questoes cadastradas com sucesso"),
        @ApiResponse(responseCode = "422", description = "Gabarito ja publicado ou ordem de questao duplicada")
    })
    public ResponseEntity<QuestoesCadastradasResponse> cadastrarQuestoes(
        @PathVariable("id") UUID provaId,
        @Valid @RequestBody CadastrarQuestoesEmLoteRequest request
    ) {
        var out = concursoUseCases.cadastrarQuestoes(request.toCommand(provaId));
        return ResponseEntity
            .created(URI.create("/provas/" + provaId + "/questoes"))
            .body(QuestoesCadastradasResponse.from(out));
    }

    @PutMapping("/provas/{id}/gabarito")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publica ou atualiza o gabarito oficial da prova (dispara GabaritoPublicado)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Gabarito publicado/atualizado com sucesso"),
        @ApiResponse(responseCode = "422", description = "Gabarito definitivo imutavel ou questoes faltantes")
    })
    public ResponseEntity<GabaritoPublicadoResponse> publicarGabarito(
        @PathVariable("id") UUID provaId,
        @Valid @RequestBody PublicarGabaritoRequest request
    ) {
        var out = concursoUseCases.publicarOuAtualizarGabarito(request.toCommand(provaId));
        return ResponseEntity.ok(GabaritoPublicadoResponse.from(out));
    }
}
