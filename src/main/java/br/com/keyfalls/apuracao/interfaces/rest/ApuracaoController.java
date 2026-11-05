package br.com.keyfalls.apuracao.interfaces.rest;

import br.com.keyfalls.apuracao.application.ApuracaoUseCases;
import br.com.keyfalls.apuracao.application.ApuracaoUseCases.ImportarFichasOpticasCommand;
import br.com.keyfalls.apuracao.interfaces.rest.dto.ApuracaoDtos.ApuracaoDisparadaResponse;
import br.com.keyfalls.apuracao.interfaces.rest.dto.ApuracaoDtos.BoletimDesempenhoResponse;
import br.com.keyfalls.apuracao.interfaces.rest.dto.ApuracaoDtos.CandidatoClassificadoResponse;
import br.com.keyfalls.apuracao.interfaces.rest.dto.ApuracaoDtos.GabaritoVigenteResponse;
import br.com.keyfalls.apuracao.interfaces.rest.dto.ApuracaoDtos.ImportacaoFichasResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RestController
@Tag(name = "4. Apuracao, Classificacao e Desempenho", description = "Captura optica de fichas, processamento de notas ponderadas e consulta de resultados")
public class ApuracaoController {

    private static final long TAMANHO_MAXIMO_ARQUIVO_BYTES = 20L * 1024L * 1024L; // 20 MB
    private static final Set<String> EXTENSOES_PERMITIDAS = Set.of("csv", "txt");

    private final ApuracaoUseCases apuracaoUseCases;

    public ApuracaoController(ApuracaoUseCases apuracaoUseCases) {
        this.apuracaoUseCases = Objects.requireNonNull(apuracaoUseCases);
    }

    @PostMapping(value = "/fichas/importacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerJwt")
    @Operation(summary = "Importa lote de fichas opticas (CSV/TXT) oriundas da leitora optica")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Arquivo recebido e fichas importadas"),
        @ApiResponse(responseCode = "400", description = "Arquivo vazio, formato nao suportado ou tamanho excedido")
    })
    public ResponseEntity<ImportacaoFichasResponse> importarFichas(
        @RequestParam("concursoId") UUID concursoId,
        @RequestPart("arquivo") MultipartFile arquivo
    ) throws IOException {
        validarUploadSeguro(arquivo);
        String nomeSanitizado = StringUtils.cleanPath(Objects.requireNonNullElse(arquivo.getOriginalFilename(), "fichas.csv"));

        var out = apuracaoUseCases.importarFichasOpticas(new ImportarFichasOpticasCommand(
            concursoId,
            nomeSanitizado,
            arquivo.getContentType(),
            arquivo.getBytes()
        ));
        return ResponseEntity.accepted().body(ImportacaoFichasResponse.from(out));
    }

    @PostMapping("/concursos/{id}/apuracao")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerJwt")
    @Operation(summary = "Dispara o calculo ponderado de notas e geracao da classificacao")
    public ResponseEntity<ApuracaoDisparadaResponse> dispararApuracao(@PathVariable("id") UUID concursoId) {
        var out = apuracaoUseCases.processarApuracaoEClassificacao(concursoId);
        return ResponseEntity.accepted().body(ApuracaoDisparadaResponse.from(out));
    }

    @GetMapping("/concursos/{id}/classificados")
    @Operation(summary = "Lista publica oficial dos candidatos classificados em ordem decrescente de nota")
    public ResponseEntity<List<CandidatoClassificadoResponse>> listarClassificados(
        @PathVariable("id") UUID concursoId,
        @RequestParam(value = "cargoId", required = false) UUID cargoId
    ) {
        var lista = apuracaoUseCases.listarClassificados(concursoId, cargoId).stream()
            .map(CandidatoClassificadoResponse::from)
            .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/candidatos/me/desempenho")
    @PreAuthorize("hasRole('CANDIDATO')")
    @SecurityRequirement(name = "bearerJwt")
    @Operation(summary = "Boletim individual restrito do candidato autenticado (anti-IDOR via JWT sub)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Desempenho individual detalhado"),
        @ApiResponse(responseCode = "404", description = "Resultado ainda nao disponivel para o candidato")
    })
    public ResponseEntity<BoletimDesempenhoResponse> consultarMeuDesempenho(
        @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
        @RequestParam("concursoId") UUID concursoId
    ) {
        UUID candidatoAutenticadoId = UUID.fromString(jwt.getSubject());
        var out = apuracaoUseCases.consultarMeuDesempenho(candidatoAutenticadoId, concursoId);
        return ResponseEntity.ok(BoletimDesempenhoResponse.from(out));
    }

    @GetMapping("/provas/{id}/gabarito")
    @Operation(summary = "Consulta publica do gabarito vigente (preliminar ou definitivo) da prova")
    public ResponseEntity<GabaritoVigenteResponse> consultarGabaritoVigente(@PathVariable("id") UUID provaId) {
        var out = apuracaoUseCases.consultarGabaritoVigente(provaId);
        return ResponseEntity.ok(GabaritoVigenteResponse.from(out));
    }

    private static void validarUploadSeguro(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo de fichas de resposta nao pode estar vazio.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO_ARQUIVO_BYTES) {
            throw new IllegalArgumentException("O arquivo excede o tamanho maximo permitido de 20 MB.");
        }
        String nome = arquivo.getOriginalFilename();
        String extensao = StringUtils.getFilenameExtension(nome);
        if (extensao == null || !EXTENSOES_PERMITIDAS.contains(extensao.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Formato invalido. Envie apenas arquivos .csv ou .txt.");
        }
    }
}
