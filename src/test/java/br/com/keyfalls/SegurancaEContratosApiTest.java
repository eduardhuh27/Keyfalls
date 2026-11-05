package br.com.keyfalls;

import br.com.keyfalls.apuracao.application.ApuracaoUseCases;
import br.com.keyfalls.apuracao.interfaces.rest.ApuracaoController;
import br.com.keyfalls.concurso.application.ConcursoUseCases;
import br.com.keyfalls.concurso.interfaces.rest.ConcursoController;
import br.com.keyfalls.inscricao.application.InscricaoConsultasEIsencaoUseCases;
import br.com.keyfalls.inscricao.application.RealizarInscricaoUseCase;
import br.com.keyfalls.inscricao.interfaces.rest.InscricaoController;
import br.com.keyfalls.inscricao.interfaces.rest.mapper.InscricaoApiMapper;
import br.com.keyfalls.logistica.application.LogisticaUseCases;
import br.com.keyfalls.logistica.interfaces.rest.LogisticaController;
import br.com.keyfalls.recurso.application.RecursoUseCases;
import br.com.keyfalls.recurso.interfaces.rest.RecursoController;
import br.com.keyfalls.shared.domain.exception.PropriedadeRecursoVioladaException;
import br.com.keyfalls.shared.domain.exception.RecursosPendentesException;
import br.com.keyfalls.shared.infrastructure.security.JwtProperties;
import br.com.keyfalls.shared.infrastructure.security.SecurityConfig;
import br.com.keyfalls.shared.interfaces.rest.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suite Adversarial de Seguranca (Spring Security 6 OAuth2 JWT + MockMvc) e Contratos REST (RFC 7807 ProblemDetail).
 * Cobre ataques de IDOR entre candidatos, escalacao horizontal/vertical de privilegios (RBAC)
 * e rejeicao sintatica/semantica de payloads maliciosos ou invalidos.
 */
@WebMvcTest(controllers = {
    ConcursoController.class,
    InscricaoController.class,
    LogisticaController.class,
    ApuracaoController.class,
    RecursoController.class
})
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    SegurancaEContratosApiTest.JwtTestConfig.class
})
@TestPropertySource(properties = {
    "keyfalls.security.jwt.issuer=https://auth.keyfalls.com.br",
    "keyfalls.security.jwt.secret=chave-secreta-exclusiva-para-testes-automatizados-256-bits-keyfalls",
    "keyfalls.security.jwt.expiration-minutes=60"
})
class SegurancaEContratosApiTest {

    @TestConfiguration
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConcursoUseCases concursoUseCases;

    @MockBean
    private RealizarInscricaoUseCase realizarInscricaoUseCase;

    @MockBean
    private InscricaoConsultasEIsencaoUseCases consultasEIsencaoUseCases;

    @MockBean
    private InscricaoApiMapper inscricaoApiMapper;

    @MockBean
    private LogisticaUseCases logisticaUseCases;

    @MockBean
    private ApuracaoUseCases apuracaoUseCases;

    @MockBean
    private RecursoUseCases recursoUseCases;
// 1. PREVENCAO DE IDOR (Insecure Direct Object Reference)
@Test
    @DisplayName("Anti-IDOR: Candidato A autenticado tentando ler boleto do Candidato B recebe 403 Forbidden (ProblemDetail)")
    void deveBloquearIdorQuandoCandidatoATentarAcessarBoletoDoCandidatoB() throws Exception {
        UUID candidatoAId = UUID.randomUUID();
        UUID inscricaoDoCandidatoB = UUID.randomUUID();

        when(consultasEIsencaoUseCases.consultarBoleto(inscricaoDoCandidatoB, candidatoAId, false))
            .thenThrow(new PropriedadeRecursoVioladaException(
                "Acesso negado: a inscricao solicitada pertence a outro candidato."
            ));

        mockMvc.perform(
                get("/inscricoes/{id}/boleto", inscricaoDoCandidatoB)
                    .with(tokenComPerfil(candidatoAId, "ROLE_CANDIDATO"))
            )
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.title").value("Acesso Negado ao Recurso"))
            .andExpect(jsonPath("$.type").value("https://api.keyfalls.com.br/erros/acesso-negado"));
    }

    @Test
    @DisplayName("Anti-IDOR: Candidato A autenticado tentando consultar pedido de isencao do Candidato B recebe 403 Forbidden")
    void deveBloquearIdorQuandoCandidatoATentarConsultarIsencaoDoCandidatoB() throws Exception {
        UUID candidatoAId = UUID.randomUUID();
        UUID isencaoDoCandidatoB = UUID.randomUUID();

        when(consultasEIsencaoUseCases.consultarIsencao(isencaoDoCandidatoB, candidatoAId, false))
            .thenThrow(new PropriedadeRecursoVioladaException(
                "Acesso negado: o pedido de isencao pertence a outro candidato."
            ));

        mockMvc.perform(
                get("/isencoes/{id}", isencaoDoCandidatoB)
                    .with(tokenComPerfil(candidatoAId, "ROLE_CANDIDATO"))
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
    }
// 2. VIOLACOES DE RBAC (ROLE_CANDIDATO, ROLE_FISCAL, ANONIMO)
@Test
    @DisplayName("RBAC: Requisicao sem token Bearer JWT em endpoint protegido deve retornar 401 Unauthorized")
    void deveRetornar401QuandoSemTokenJwt() throws Exception {
        mockMvc.perform(get("/candidatos/me/desempenho").param("concursoId", UUID.randomUUID().toString()))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(apuracaoUseCases);
    }

    @Test
    @DisplayName("RBAC: ROLE_CANDIDATO tentando alterar gabarito, julgar recurso ou lancar lista de presenca deve receber 403 Forbidden")
    void candidatoNaoPodeAlterarGabaritoJulgarRecursoOuRegistrarPresenca() throws Exception {
        UUID candidatoId = UUID.randomUUID();
        var jwtCandidato = tokenComPerfil(candidatoId, "ROLE_CANDIDATO");

        // 1. Tentativa de adulterar gabarito oficial
        mockMvc.perform(
                put("/provas/{id}/gabarito", UUID.randomUUID())
                    .with(jwtCandidato)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"concursoId":"%s","definitivo":true,"respostas":{"%s":"A"}}
                        """.formatted(UUID.randomUUID(), UUID.randomUUID()))
            )
            .andExpect(status().isForbidden());

        // 2. Tentativa de auto-deferir recurso proprio
        mockMvc.perform(
                put("/recursos/{id}/julgamento", UUID.randomUUID())
                    .with(jwtCandidato)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"deferido":true,"anularQuestao":true,"parecerBanca":"Auto-aprovado maliciosamente"}
                        """)
            )
            .andExpect(status().isForbidden());

        // 3. Tentativa de assinar lista de presenca de sala (exclusivo FISCAL/ADMIN)
        mockMvc.perform(
                post("/salas/{id}/presencas", UUID.randomUUID())
                    .with(jwtCandidato)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"itens":[{"alocacaoId":"%s","presente":true}]}
                        """.formatted(UUID.randomUUID()))
            )
            .andExpect(status().isForbidden());

        verifyNoInteractions(concursoUseCases, recursoUseCases, logisticaUseCases);
    }

    @Test
    @DisplayName("RBAC: ROLE_FISCAL nao pode julgar/anular questao nem homologar resultado final do concurso (403 Forbidden)")
    void fiscalNaoPodeAnularQuestaoNemPublicarResultadoFinal() throws Exception {
        var jwtFiscal = tokenComPerfil(UUID.randomUUID(), "ROLE_FISCAL");

        mockMvc.perform(
                put("/recursos/{id}/julgamento", UUID.randomUUID())
                    .with(jwtFiscal)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"deferido":true,"anularQuestao":true,"parecerBanca":"Tentativa indevida por fiscal"}
                        """)
            )
            .andExpect(status().isForbidden());

        mockMvc.perform(
                post("/concursos/{id}/resultado-final", UUID.randomUUID())
                    .with(jwtFiscal)
            )
            .andExpect(status().isForbidden());

        verifyNoInteractions(recursoUseCases);
    }
// 3. CONTRATOS REST, BEAN VALIDATION (400), UPLOAD MALICIOSO E CONFLITO (409)
@Test
    @DisplayName("Contrato 400 ProblemDetail: deve rejeitar payload invalido na abertura de recurso com mapa detalhado de violacoes")
    void deveRetornar400ProblemDetailQuandoPayloadDeRecursoForInvalido() throws Exception {
        mockMvc.perform(
                post("/recursos")
                    .with(tokenComPerfil(UUID.randomUUID(), "ROLE_CANDIDATO"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "concursoId": null,
                          "provaId": null,
                          "questaoId": null,
                          "fundamentacao": "curto"
                        }
                        """)
            )
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Erro de Validacao Sintatica"))
            .andExpect(jsonPath("$.violacoes.concursoId").exists())
            .andExpect(jsonPath("$.violacoes.fundamentacao").exists());

        verifyNoInteractions(recursoUseCases);
    }

    @Test
    @DisplayName("Upload Seguro (400): deve rejeitar extensao executavel (.exe) ou arquivo vazio em POST /fichas/importacao")
    void deveRejeitarUploadDeExtensaoProibidaNoImportadorDeFichas() throws Exception {
        MockMultipartFile arquivoMalicioso = new MockMultipartFile(
            "arquivo", "exploit.exe", "application/octet-stream", "MZ-payload".getBytes()
        );

        mockMvc.perform(
                multipart("/fichas/importacao")
                    .file(arquivoMalicioso)
                    .param("concursoId", UUID.randomUUID().toString())
                    .with(tokenComPerfil(UUID.randomUUID(), "ROLE_ADMIN"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.detail").value("Formato invalido. Envie apenas arquivos .csv ou .txt."));

        verifyNoInteractions(apuracaoUseCases);
    }

    @Test
    @DisplayName("Trava 409 Conflict ProblemDetail: POST /concursos/{id}/resultado-final bloqueia homologacao se houver recursos pendentes")
    void deveRetornar409ConflictAoPublicarResultadoFinalComRecursoPendente() throws Exception {
        UUID concursoId = UUID.randomUUID();
        when(recursoUseCases.homologarResultadoFinal(concursoId))
            .thenThrow(new RecursosPendentesException(
                "Existem 3 recursos com status PENDENTE aguardando parecer da banca."
            ));

        mockMvc.perform(
                post("/concursos/{id}/resultado-final", concursoId)
                    .with(tokenComPerfil(UUID.randomUUID(), "ROLE_ADMIN"))
            )
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.type").value("https://api.keyfalls.com.br/erros/conflito-negocio"));
    }

    private static JwtRequestPostProcessor tokenComPerfil(UUID subjectId, String role) {
        return jwt()
            .jwt(builder -> builder
                .subject(subjectId.toString())
                .claim("roles", List.of(role))
            )
            .authorities(new SimpleGrantedAuthority(role));
    }
}
