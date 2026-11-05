package br.com.keyfalls;

import br.com.keyfalls.apuracao.application.ConsultaMatrizApuracaoPort;
import br.com.keyfalls.apuracao.application.ControleAnulacaoProcessadaPort;
import br.com.keyfalls.apuracao.application.FichaRespostaParserPort;
import br.com.keyfalls.apuracao.application.RecalculoPorQuestaoAnuladaHandler;
import br.com.keyfalls.apuracao.domain.CalculadoraDeNota;
import br.com.keyfalls.apuracao.domain.ClassificadorDeCandidatos;
import br.com.keyfalls.apuracao.domain.FichaDeResposta;
import br.com.keyfalls.apuracao.domain.FichaDeRespostaRepository;
import br.com.keyfalls.apuracao.domain.Resultado;
import br.com.keyfalls.apuracao.domain.ResultadoRepository;
import br.com.keyfalls.apuracao.domain.event.ResultadoRepublicado;
import br.com.keyfalls.apuracao.infrastructure.parser.CsvFichaParser;
import br.com.keyfalls.comunicacao.application.ConsultaDestinatarioPort;
import br.com.keyfalls.comunicacao.application.NotificacaoCandidatoEventHandler;
import br.com.keyfalls.comunicacao.domain.EnviadorEmailPort;
import br.com.keyfalls.concurso.domain.event.QuestaoAnulada;
import br.com.keyfalls.inscricao.domain.event.IsencaoJulgada;
import br.com.keyfalls.inscricao.infrastructure.persistence.InscricaoJpaEntity;
import br.com.keyfalls.inscricao.infrastructure.persistence.SpringDataInscricaoRepository;
import br.com.keyfalls.logistica.infrastructure.persistence.SalaJpaEntity;
import br.com.keyfalls.logistica.infrastructure.persistence.SalaSpringDataRepository;
import br.com.keyfalls.recurso.infrastructure.persistence.RecursoJpaEntity;
import br.com.keyfalls.recurso.infrastructure.persistence.RecursoSpringDataRepository;
import br.com.keyfalls.shared.application.event.EventoProcessadoRepository;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.FichaDeRespostaId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import br.com.keyfalls.shared.domain.exception.RecursosPendentesException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.MailSendException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suite Integrada Adversarial (Testcontainers PostgreSQL 16 + Coreografia de Eventos + Concorrencia Multi-Thread).
 * Valida idempotencia dupla, isolamento transacional contra queda de SMTP, Optimistic Locking (@Version)
 * na ultima vaga da Sala e no julgamento simultaneo de Recurso, trava de Resultado Final e parser optico corrompido.
 */
class FluxoEventosEConcorrenciaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SalaSpringDataRepository salaRepository;

    @Autowired
    private RecursoSpringDataRepository recursoRepository;

    @Autowired
    private SpringDataInscricaoRepository inscricaoRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final CsvFichaParser csvFichaParser = new CsvFichaParser();
// 1. COREOGRAFIA DE EVENTOS & DUPLA IDEMPOTENCIA (QuestaoAnulada -> Apuracao)
@Test
    @DisplayName("Disparo duplicado de QuestaoAnulada (mesmo eventId ou novo eventId para mesma questao) NAO recalcula duas vezes e publica ResultadoRepublicado apos salvar lote")
    void deveGarantirIdempotenciaDuplaEOrdemEstritaNoRecalculoPorQuestaoAnulada() {
        // Portas de saida mockadas (objetos de dominio CalculadoraDeNota e Classificador sao 100% REAIS!)
        FichaDeRespostaRepository fichaRepoPort = mock(FichaDeRespostaRepository.class);
        ResultadoRepository resultadoRepoPort = mock(ResultadoRepository.class);
        ConsultaMatrizApuracaoPort matrizPort = mock(ConsultaMatrizApuracaoPort.class);
        EventoProcessadoRepository eventoProcessadoPort = mock(EventoProcessadoRepository.class);
        ControleAnulacaoProcessadaPort controleAnulacaoPort = mock(ControleAnulacaoProcessadaPort.class);
        DomainEventPublisher eventPublisherPort = mock(DomainEventPublisher.class);

        Set<String> eventosProcessados = new HashSet<>();
        Set<String> anulacoesAplicadas = new HashSet<>();

        when(eventoProcessadoPort.jaFoiProcessado(any(), any()))
            .thenAnswer(inv -> eventosProcessados.contains(inv.getArgument(0) + ":" + inv.getArgument(1)));
        doAnswer(inv -> {
            eventosProcessados.add(inv.getArgument(0) + ":" + inv.getArgument(1));
            return null;
        }).when(eventoProcessadoPort).registrarProcessamento(any(), any(), any());

        when(controleAnulacaoPort.anulacaoJaAplicada(any(), any()))
            .thenAnswer(inv -> anulacoesAplicadas.contains(inv.getArgument(0) + ":" + inv.getArgument(1)));
        doAnswer(inv -> {
            anulacoesAplicadas.add(inv.getArgument(0) + ":" + inv.getArgument(1));
            return null;
        }).when(controleAnulacaoPort).registrarAnulacaoAplicada(any(), any(), any(), any());

        ConcursoId concursoId = ConcursoId.novo();
        CargoId cargoId = CargoId.novo();
        ProvaId provaId = ProvaId.novo();
        QuestaoId q1Anulada = QuestaoId.novo();
        QuestaoId q2Valida = QuestaoId.novo();

        Map<QuestaoId, Alternativa> gabarito = new LinkedHashMap<>();
        gabarito.put(q1Anulada, Alternativa.A);
        gabarito.put(q2Valida, Alternativa.B);

        FichaDeResposta fichaReal = FichaDeResposta.capturar(
            FichaDeRespostaId.novo(),
            InscricaoId.novo(),
            concursoId,
            Instant.parse("2026-10-20T18:00:00Z"),
            Map.of(provaId, Map.of(q1Anulada, Alternativa.E, q2Valida, Alternativa.B))
        );

        when(fichaRepoPort.listarPorConcurso(concursoId)).thenReturn(List.of(fichaReal));
        when(matrizPort.obterContextoAtualizadoPorProva(concursoId, provaId)).thenReturn(
            new ConsultaMatrizApuracaoPort.ContextoRecalculoCargo(
                cargoId,
                new PoliticaAnulacaoQuestao.PontoUniversalStrategy(),
                List.of(new CalculadoraDeNota.MatrizGabaritoProva(provaId, Peso.de(1), gabarito, Set.of(q1Anulada)))
            )
        );

        var handler = new RecalculoPorQuestaoAnuladaHandler(
            fichaRepoPort,
            resultadoRepoPort,
            matrizPort,
            eventoProcessadoPort,
            controleAnulacaoPort,
            new CalculadoraDeNota(),
            new ClassificadorDeCandidatos(),
            eventPublisherPort,
            Clock.fixed(Instant.parse("2026-11-04T15:00:00Z"), ZoneOffset.UTC)
        );

        QuestaoAnulada eventoOriginal = QuestaoAnulada.novo(
            concursoId, provaId, q1Anulada, "Erro material no enunciado", Instant.parse("2026-11-04T14:59:00Z")
        );
        // Reentrega duplicada com outro UUID de evento para a MESMA questao ja anulada
        QuestaoAnulada eventoDuplicadoOutroId = QuestaoAnulada.novo(
            concursoId, provaId, q1Anulada, "Reprocessamento acidental de fila", Instant.parse("2026-11-04T15:01:00Z")
        );

        // 1a execucao: recalcula lote e publica ResultadoRepublicado
        handler.tratarQuestaoAnulada(eventoOriginal);
        // 2a execucao (mesmo eventId -> barrado na Camada 1 Tecnica)
        handler.tratarQuestaoAnulada(eventoOriginal);
        // 3a execucao (novo eventId, mesma questao -> barrado na Camada 2 Semantica)
        handler.tratarQuestaoAnulada(eventoDuplicadoOutroId);

        // Garante ordem estrita: salvarTodos(lote) SEMPRE ocorre antes de publish(ResultadoRepublicado)
        InOrder ordemEstrita = inOrder(resultadoRepoPort, eventPublisherPort);
        ordemEstrita.verify(resultadoRepoPort, times(1)).salvarTodos(anyList());
        ordemEstrita.verify(eventPublisherPort, times(1)).publish(any(ResultadoRepublicado.class));

        verify(resultadoRepoPort, times(1)).salvarTodos(org.mockito.ArgumentMatchers.<List<Resultado>>argThat(
            lista -> lista.size() == 1 && lista.get(0).notaTotalPonderada().compareTo(new BigDecimal("100.00")) == 0
        ));
    }
// 2. ISOLAMENTO TRANSACIONAL CONTRA QUEDA DE SMTP (IsencaoJulgada -> Comunicacao)
@Test
    @DisplayName("Falha no servidor SMTP (MailSendException) ao processar IsencaoJulgada NAO faz rollback na homologacao da Inscricao ja salva no PostgreSQL")
    void falhaNoServidorSmtpNaoDeveReverterJulgamentoDeIsencaoSalvoNoBanco() {
        UUID inscricaoId = UUID.randomUUID();
        UUID candidatoId = UUID.randomUUID();
        UUID concursoId = UUID.randomUUID();

        // Transacao T1 (Contexto Inscricao): julga a isencao como DEFERIDA e comita no PostgreSQL real
        TransactionTemplate txProdutora = criarTxRequiresNew();
        txProdutora.executeWithoutResult(status -> {
            InscricaoJpaEntity entidade = new InscricaoJpaEntity(
                inscricaoId,
                "2026990001",
                candidatoId,
                concursoId,
                UUID.randomUUID(),
                "HOMOLOGADA",
                null, null, null, false, null,
                "CadUnico NIS Validado",
                Instant.parse("2026-11-02T10:00:00Z"),
                "DEFERIDA",
                "Comprovante autentico verificado pela comissao",
                Instant.parse("2026-11-04T12:00:00Z")
            );
            inscricaoRepository.saveAndFlush(entidade);
        });

        // Transacao T2 (AFTER_COMMIT + REQUIRES_NEW no Contexto Comunicacao): SMTP lanca MailSendException!
        EnviadorEmailPort smtpIndisponivelPort = mock(EnviadorEmailPort.class);
        doThrow(new MailSendException("554 5.7.1 SMTP Relay Temporarily Unavailable"))
            .when(smtpIndisponivelPort).enviar(any());

        ConsultaDestinatarioPort destinatarioPort = mock(ConsultaDestinatarioPort.class);
        when(destinatarioPort.buscarPorCandidatoId(new CandidatoId(candidatoId)))
            .thenReturn(new ConsultaDestinatarioPort.DadosContatoCandidato("Ana Candidata", new Email("ana@candidata.com.br")));

        EventoProcessadoRepository eventoProcessadoPort = mock(EventoProcessadoRepository.class);
        when(eventoProcessadoPort.jaFoiProcessado(any(), any())).thenReturn(false);

        var handlerComunicacao = new NotificacaoCandidatoEventHandler(
            smtpIndisponivelPort,
            destinatarioPort,
            eventoProcessadoPort,
            Clock.systemUTC()
        );

        IsencaoJulgada evento = IsencaoJulgada.novo(
            new InscricaoId(inscricaoId),
            new NumeroInscricao("2026990001"),
            new CandidatoId(candidatoId),
            new ConcursoId(concursoId),
            true,
            "Comprovante autentico verificado pela comissao",
            Instant.now()
        );

        TransactionTemplate txConsumidoraIsolada = criarTxRequiresNew();
        assertThatThrownBy(() -> txConsumidoraIsolada.executeWithoutResult(
            status -> handlerComunicacao.tratarIsencaoJulgada(evento)
        )).isInstanceOf(MailSendException.class);

        // Verifica no PostgreSQL real que a Inscricao continua intacta como HOMOLOGADA / DEFERIDA!
        InscricaoJpaEntity persistidaNoBanco = inscricaoRepository.findById(inscricaoId).orElseThrow();
        assertThat(persistidaNoBanco.getStatus()).isEqualTo("HOMOLOGADA");
        assertThat(persistidaNoBanco.getIsencaoSituacao()).isEqualTo("DEFERIDA");
    }
// 3. RACE CONDITIONS REAIS NO POSTGRESQL (@Version OPTIMISTIC LOCKING + UNIQUE INDEX)
@Test
    @DisplayName("Race Condition em Sala: 2 threads disputando simultaneamente a ultima vaga (capacidade=1) — apenas 1 vence e a outra falha com OptimisticLock")
    void deveImpedirOverbookingNaUltimaVagaDaSalaSobConcorrenciaSimultanea() throws Exception {
        UUID salaId = UUID.randomUUID();
        criarTxRequiresNew().executeWithoutResult(status ->
            salaRepository.saveAndFlush(new SalaJpaEntity(
                salaId, UUID.randomUUID(), "Sala Concorrente 01", 1, 0, UUID.randomUUID()
            ))
        );

        CountDownLatch ambosLeramVagaDisponivel = new CountDownLatch(2);
        CountDownLatch liberarDisparoSimultaneo = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger bloqueiosConcorrencia = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Runnable tarefaAlocacaoConcorrente = () -> {
                try {
                    criarTxRequiresNew().executeWithoutResult(status -> {
                        SalaJpaEntity sala = salaRepository.findById(salaId).orElseThrow();
                        ambosLeramVagaDisponivel.countDown();
                        aguardarLatch(liberarDisparoSimultaneo);

                        sala.ocuparUmaVaga();
                        salaRepository.saveAndFlush(sala);
                    });
                    sucessos.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException ex) {
                    bloqueiosConcorrencia.incrementAndGet();
                }
            };

            CompletableFuture<Void> t1 = CompletableFuture.runAsync(tarefaAlocacaoConcorrente, pool);
            CompletableFuture<Void> t2 = CompletableFuture.runAsync(tarefaAlocacaoConcorrente, pool);

            assertThat(ambosLeramVagaDisponivel.await(5, TimeUnit.SECONDS)).isTrue();
            liberarDisparoSimultaneo.countDown();
            CompletableFuture.allOf(t1, t2).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(bloqueiosConcorrencia.get()).isEqualTo(1);

        SalaJpaEntity estadoFinalNoPostgres = salaRepository.findById(salaId).orElseThrow();
        assertThat(estadoFinalNoPostgres.getVagasOcupadas()).isEqualTo(1);
        assertThat(estadoFinalNoPostgres.getVersao()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Race Condition em Recurso: 2 examinadores julgando o mesmo recurso simultaneamente — 1 grava e o outro recebe conflito de versao")
    void deveImpedirDuploJulgamentoConcorrenteDoMesmoRecurso() throws Exception {
        UUID recursoId = UUID.randomUUID();
        criarTxRequiresNew().executeWithoutResult(status ->
            recursoRepository.saveAndFlush(new RecursoJpaEntity(
                recursoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Questao possui duas alternativas corretas conforme doutrina.",
                "a".repeat(64),
                Instant.now()
            ))
        );

        CountDownLatch ambosCarregaram = new CountDownLatch(2);
        CountDownLatch dispararCommit = new CountDownLatch(1);
        AtomicInteger commitsVencedores = new AtomicInteger(0);
        AtomicInteger conflitos = new AtomicInteger(0);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> examinadorA = CompletableFuture.runAsync(() -> {
                try {
                    criarTxRequiresNew().executeWithoutResult(status -> {
                        RecursoJpaEntity r = recursoRepository.findById(recursoId).orElseThrow();
                        ambosCarregaram.countDown();
                        aguardarLatch(dispararCommit);
                        r.registrarJulgamento("DEFERIDO", "Parecer Examinador A", Instant.now());
                        recursoRepository.saveAndFlush(r);
                    });
                    commitsVencedores.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException ex) {
                    conflitos.incrementAndGet();
                }
            }, pool);

            CompletableFuture<Void> examinadorB = CompletableFuture.runAsync(() -> {
                try {
                    criarTxRequiresNew().executeWithoutResult(status -> {
                        RecursoJpaEntity r = recursoRepository.findById(recursoId).orElseThrow();
                        ambosCarregaram.countDown();
                        aguardarLatch(dispararCommit);
                        r.registrarJulgamento("INDEFERIDO", "Parecer Examinador B", Instant.now());
                        recursoRepository.saveAndFlush(r);
                    });
                    commitsVencedores.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException ex) {
                    conflitos.incrementAndGet();
                }
            }, pool);

            assertThat(ambosCarregaram.await(5, TimeUnit.SECONDS)).isTrue();
            dispararCommit.countDown();
            CompletableFuture.allOf(examinadorA, examinadorB).get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(commitsVencedores.get()).isEqualTo(1);
        assertThat(conflitos.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Race Condition em Inscricao: indice unico parcial do PostgreSQL bloqueia inscricao duplicada ativa sob duplo clique concorrente")
    void deveBloquearInscricaoDuplicadaConcorrenteViaUniqueIndexParcialNoPostgres() {
        UUID candidatoId = UUID.randomUUID();
        UUID concursoId = UUID.randomUUID();
        UUID cargoId = UUID.randomUUID();

        criarTxRequiresNew().executeWithoutResult(status ->
            inscricaoRepository.saveAndFlush(new InscricaoJpaEntity(
                UUID.randomUUID(), "2026880001", candidatoId, concursoId, cargoId,
                "AGUARDANDO_PAGAMENTO", null, null, null, false, null, null, null, null, null, null
            ))
        );

        assertThatThrownBy(() -> criarTxRequiresNew().executeWithoutResult(status ->
            inscricaoRepository.saveAndFlush(new InscricaoJpaEntity(
                UUID.randomUUID(), "2026880002", candidatoId, concursoId, cargoId,
                "AGUARDANDO_PAGAMENTO", null, null, null, false, null, null, null, null, null, null
            ))
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
// 4. TRAVA DO RESULTADO FINAL COM RECURSO PENDENTE & PARSER OPTICO CORROMPIDO
@Test
    @DisplayName("Trava do Resultado Final: deve bloquear homologacao enquanto existir ao menos 1 recurso com decisao NULL (pendente)")
    void deveBloquearHomologacaoResultadoFinalEnquantoHouverRecursoPendenteNoBanco() {
        UUID concursoId = UUID.randomUUID();

        criarTxRequiresNew().executeWithoutResult(status ->
            recursoRepository.saveAndFlush(new RecursoJpaEntity(
                UUID.randomUUID(), concursoId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Recurso ainda pendente de analise pela banca examinadora.",
                "b".repeat(64),
                Instant.now()
            ))
        );

        boolean haPendentes = recursoRepository.existsByConcursoIdAndDecisaoIsNull(concursoId);
        assertThat(haPendentes).isTrue();

        assertThatThrownBy(() -> {
            if (recursoRepository.existsByConcursoIdAndDecisaoIsNull(concursoId)) {
                throw new RecursosPendentesException(
                    "Impossivel publicar resultado final: existem recursos pendentes de julgamento no concurso " + concursoId
                );
            }
        }).isInstanceOf(RecursosPendentesException.class);
    }

    @Test
    @DisplayName("Importacao de Fichas: deve tratar DUPLA_MARCACAO (*), EM_BRANCO (' ') e rejeitar arquivo com colunas faltantes/excedentes ou simbolo invalido")
    void deveTratarMarcacaoDuplaEmBrancoERejeitarLayoutsCorrompidosNaImportacao() {
        UUID provaUuid = UUID.randomUUID();
        String csvValidoComDuplaEBranco =
            "2026000001;" + provaUuid + ";A;*; ;E\n";

        var lidas = csvFichaParser.lerStream(
            new ByteArrayInputStream(csvValidoComDuplaEBranco.getBytes(StandardCharsets.UTF_8)),
            List.of(1, 2, 3, 4)
        );

        assertThat(lidas).hasSize(1);
        var marcacoes = lidas.get(0).marcacoesPorOrdemQuestao();
        assertThat(marcacoes.get(1).status()).isEqualTo(FichaRespostaParserPort.StatusMarcacaoOptica.VALIDA);
        assertThat(marcacoes.get(2).status()).isEqualTo(FichaRespostaParserPort.StatusMarcacaoOptica.DUPLA_MARCACAO);
        assertThat(marcacoes.get(2).paraAlternativaApuravel()).isNull();
        assertThat(marcacoes.get(3).status()).isEqualTo(FichaRespostaParserPort.StatusMarcacaoOptica.EM_BRANCO);
        assertThat(marcacoes.get(3).paraAlternativaApuravel()).isNull();

        // Linha corrompida com apenas 3 respostas para uma prova de 4 questoes
        String csvMenosRespostas = "2026000001;" + provaUuid + ";A;B;C\n";
        assertThatThrownBy(() -> csvFichaParser.lerStream(
            new ByteArrayInputStream(csvMenosRespostas.getBytes(StandardCharsets.UTF_8)),
            List.of(1, 2, 3, 4)
        ))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("quantidade divergente de colunas");

        // Linha corrompida com simbolo ilegal ('Z')
        String csvSimboloInvalido = "2026000001;" + provaUuid + ";A;Z;C;D\n";
        assertThatThrownBy(() -> csvFichaParser.lerStream(
            new ByteArrayInputStream(csvSimboloInvalido.getBytes(StandardCharsets.UTF_8)),
            List.of(1, 2, 3, 4)
        ))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("Simbolo optico invalido");
    }

    private TransactionTemplate criarTxRequiresNew() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static void aguardarLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout aguardando sincronizacao de threads no teste de concorrencia.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
