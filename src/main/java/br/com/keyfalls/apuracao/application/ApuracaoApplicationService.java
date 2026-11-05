package br.com.keyfalls.apuracao.application;

import br.com.keyfalls.apuracao.application.ConsultaMatrizApuracaoPort.ContextoRecalculoCargo;
import br.com.keyfalls.apuracao.application.ConsultaMatrizApuracaoPort.MetadadosCandidatoInscricao;
import br.com.keyfalls.apuracao.domain.CalculadoraDeNota;
import br.com.keyfalls.apuracao.domain.ClassificadorDeCandidatos;
import br.com.keyfalls.apuracao.domain.ClassificadorDeCandidatos.ItemClassificacao;
import br.com.keyfalls.apuracao.domain.FichaDeResposta;
import br.com.keyfalls.apuracao.domain.FichaDeRespostaRepository;
import br.com.keyfalls.apuracao.domain.NotaProva;
import br.com.keyfalls.apuracao.domain.Resultado;
import br.com.keyfalls.apuracao.domain.ResultadoRepository;
import br.com.keyfalls.apuracao.domain.event.FichasProcessadas;
import br.com.keyfalls.apuracao.domain.event.NotasCalculadas;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.FichaDeRespostaId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementacao concreta dos Use Cases do Bounded Context Apuracao.
 * Orquestra a importacao de fichas opticas via FichaRespostaFileParserPort/FichaRespostaParserPort,
 * calcula notas e classificacao via CalculadoraDeNota e ClassificadorDeCandidatos,
 * persiste agregados FichaDeResposta e Resultado e publica FichasProcessadas e NotasCalculadas.
 */
public class ApuracaoApplicationService implements ApuracaoUseCases {

    private static final int TAMANHO_CHUNK_PADRAO = 500;

    private final FichaDeRespostaRepository fichaRepository;
    private final ResultadoRepository resultadoRepository;
    private final ConsultaMatrizApuracaoPort consultaMatrizPort;
    private final FichaRespostaFileParserPort fileParserPort;
    private final CalculadoraDeNota calculadoraDeNota;
    private final ClassificadorDeCandidatos classificadorDeCandidatos;
    private final DomainEventPublisher eventPublisher;

    public ApuracaoApplicationService(
        FichaDeRespostaRepository fichaRepository,
        ResultadoRepository resultadoRepository,
        ConsultaMatrizApuracaoPort consultaMatrizPort,
        FichaRespostaFileParserPort fileParserPort,
        CalculadoraDeNota calculadoraDeNota,
        ClassificadorDeCandidatos classificadorDeCandidatos,
        DomainEventPublisher eventPublisher
    ) {
        this.fichaRepository = Objects.requireNonNull(fichaRepository);
        this.resultadoRepository = Objects.requireNonNull(resultadoRepository);
        this.consultaMatrizPort = Objects.requireNonNull(consultaMatrizPort);
        this.fileParserPort = Objects.requireNonNull(fileParserPort);
        this.calculadoraDeNota = Objects.requireNonNull(calculadoraDeNota);
        this.classificadorDeCandidatos = Objects.requireNonNull(classificadorDeCandidatos);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public ImportacaoFichasOutput importarFichasOpticas(ImportarFichasOpticasCommand command) {
        Objects.requireNonNull(command, "Comando de importacao de fichas opticas obrigatorio.");
        if (command.conteudoBytes() == null || command.conteudoBytes().length == 0) {
            throw new RegraDeNegocioException("Arquivo de leitura optica vazio.");
        }

        ConcursoId concursoId = new ConcursoId(command.concursoId());
        Instant capturadaEm = Instant.now();
        AtomicInteger totalImportadas = new AtomicInteger(0);
        Map<InscricaoId, MetadadosCandidatoInscricao> metadadosPorInscricao =
            consultaMatrizPort.buscarMetadadosInscricoesDoConcurso(concursoId);
        Map<CargoId, Integer> contagemPorCargo = new LinkedHashMap<>();

        fileParserPort.processarEmChunks(
            new ByteArrayInputStream(command.conteudoBytes()),
            TAMANHO_CHUNK_PADRAO,
            chunk -> {
                Map<InscricaoId, Map<ProvaId, Map<QuestaoId, Alternativa>>> agrupadoPorInscricao = new LinkedHashMap<>();
                for (FichaRespostaFileParserPort.FichaCapturadaItem item : chunk) {
                    agrupadoPorInscricao
                        .computeIfAbsent(item.inscricaoId(), k -> new LinkedHashMap<>())
                        .put(item.provaId(), item.marcacoes());
                }

                for (Map.Entry<InscricaoId, Map<ProvaId, Map<QuestaoId, Alternativa>>> entry : agrupadoPorInscricao.entrySet()) {
                    InscricaoId inscricaoId = entry.getKey();
                    Map<ProvaId, Map<QuestaoId, Alternativa>> novasProvas = entry.getValue();

                    Optional<FichaDeResposta> existenteOpt = fichaRepository.buscarPorInscricao(inscricaoId);
                    Map<ProvaId, Map<QuestaoId, Alternativa>> consolidado = new LinkedHashMap<>();
                    FichaDeRespostaId fichaId = FichaDeRespostaId.novo();

                    if (existenteOpt.isPresent()) {
                        fichaId = existenteOpt.get().id();
                        consolidado.putAll(existenteOpt.get().respostasPorProva());
                    }
                    consolidado.putAll(novasProvas);

                    FichaDeResposta ficha = FichaDeResposta.capturar(
                        fichaId,
                        inscricaoId,
                        concursoId,
                        capturadaEm,
                        consolidado
                    );
                    fichaRepository.salvar(ficha);
                    ficha.pullDomainEvents().forEach(eventPublisher::publish);
                    totalImportadas.incrementAndGet();

                    MetadadosCandidatoInscricao meta = metadadosPorInscricao.get(inscricaoId);
                    if (meta != null) {
                        contagemPorCargo.merge(meta.cargoId(), 1, Integer::sum);
                    }
                }
            }
        );

        contagemPorCargo.forEach((cargoId, qtd) -> {
            if (qtd > 0) {
                eventPublisher.publish(FichasProcessadas.novo(concursoId, cargoId, qtd, capturadaEm));
            }
        });

        return new ImportacaoFichasOutput(
            UUID.randomUUID(),
            command.concursoId(),
            totalImportadas.get(),
            capturadaEm
        );
    }

    @Override
    public DispararApuracaoOutput processarApuracaoEClassificacao(UUID concursoId) {
        Objects.requireNonNull(concursoId, "ConcursoId obrigatorio para processamento de apuracao.");
        ConcursoId idConcurso = new ConcursoId(concursoId);
        Instant disparadoEm = Instant.now();

        List<ContextoRecalculoCargo> contextosCargos = consultaMatrizPort.listarContextosDoConcurso(idConcurso);
        Map<InscricaoId, MetadadosCandidatoInscricao> metadadosPorInscricao =
            consultaMatrizPort.buscarMetadadosInscricoesDoConcurso(idConcurso);
        List<FichaDeResposta> fichasConcurso = fichaRepository.listarPorConcurso(idConcurso);

        int totalProcessados = 0;
        int totalClassificados = 0;

        for (ContextoRecalculoCargo contexto : contextosCargos) {
            CargoId cargoId = contexto.cargoId();
            List<FichaDeResposta> fichasDoCargo = fichasConcurso.stream()
                .filter(f -> {
                    MetadadosCandidatoInscricao meta = metadadosPorInscricao.get(f.inscricaoId());
                    return meta != null && meta.cargoId().equals(cargoId);
                })
                .toList();

            if (fichasDoCargo.isEmpty()) {
                continue;
            }

            List<Resultado> resultadosDoCargo = new ArrayList<>(fichasDoCargo.size());
            for (FichaDeResposta ficha : fichasDoCargo) {
                Resultado resultado = calculadoraDeNota.apurarResultado(
                    ficha,
                    cargoId,
                    contexto.matrizesAtualizadasDoCargo(),
                    contexto.politicaAnulacao()
                );
                resultadosDoCargo.add(resultado);
            }

            List<ItemClassificacao> classificadosCargo = classificadorDeCandidatos.classificarAprovados(resultadosDoCargo);
            resultadoRepository.salvarTodos(resultadosDoCargo);
            for (Resultado r : resultadosDoCargo) {
                r.pullDomainEvents().forEach(eventPublisher::publish);
            }

            totalProcessados += resultadosDoCargo.size();
            totalClassificados += classificadosCargo.size();

            eventPublisher.publish(NotasCalculadas.novo(
                idConcurso,
                cargoId,
                resultadosDoCargo.size(),
                classificadosCargo.size(),
                disparadoEm
            ));
        }

        return new DispararApuracaoOutput(concursoId, totalProcessados, totalClassificados, disparadoEm);
    }

    @Override
    public List<ClassificadoItemOutput> listarClassificados(UUID concursoId, UUID cargoIdOpcional) {
        Objects.requireNonNull(concursoId, "ConcursoId obrigatorio.");
        ConcursoId idConcurso = new ConcursoId(concursoId);
        Map<InscricaoId, MetadadosCandidatoInscricao> metadados =
            consultaMatrizPort.buscarMetadadosInscricoesDoConcurso(idConcurso);

        List<CargoId> cargosAlvo = (cargoIdOpcional != null)
            ? List.of(new CargoId(cargoIdOpcional))
            : consultaMatrizPort.listarContextosDoConcurso(idConcurso).stream()
                .map(ContextoRecalculoCargo::cargoId)
                .toList();

        List<ClassificadoItemOutput> saida = new ArrayList<>();
        for (CargoId cargoId : cargosAlvo) {
            List<Resultado> resultadosCargo = resultadoRepository.listarPorConcursoECargo(idConcurso, cargoId);
            List<ItemClassificacao> ranking = classificadorDeCandidatos.classificarAprovados(resultadosCargo);

            for (ItemClassificacao item : ranking) {
                MetadadosCandidatoInscricao meta = metadados.get(item.inscricaoId());
                String numInscricao = meta != null ? meta.numeroInscricao() : item.inscricaoId().valor().toString();
                String nomeCandidato = meta != null ? meta.nomeCandidato() : "Candidato";
                saida.add(new ClassificadoItemOutput(
                    item.colocacao(),
                    numInscricao,
                    nomeCandidato,
                    cargoId.valor(),
                    item.notaTotalPonderada(),
                    item.totalAcertos()
                ));
            }
        }
        return List.copyOf(saida);
    }

    @Override
    public BoletimDesempenhoCandidatoOutput consultarMeuDesempenho(UUID candidatoAutenticadoId, UUID concursoId) {
        Objects.requireNonNull(candidatoAutenticadoId, "Candidato autenticado obrigatorio.");
        Objects.requireNonNull(concursoId, "ConcursoId obrigatorio.");
        ConcursoId idConcurso = new ConcursoId(concursoId);

        MetadadosCandidatoInscricao meta = consultaMatrizPort
            .buscarInscricaoDoCandidatoNoConcurso(candidatoAutenticadoId, idConcurso)
            .orElseThrow(() -> new RegraDeNegocioException(
                "Candidato autenticado nao possui inscricao neste concurso."
            ));

        Resultado resultado = resultadoRepository.buscarPorInscricao(meta.inscricaoId())
            .orElseThrow(() -> new RegraDeNegocioException(
                "Desempenho ainda nao apurado para a inscricao: " + meta.numeroInscricao()
            ));

        Map<ProvaId, String> disciplinasPorProva = consultaMatrizPort.mapearDisciplinaPorProvaDoConcurso(idConcurso);

        List<DesempenhoProvaOutput> notasOutput = new ArrayList<>();
        for (NotaProva np : resultado.notasPorProva()) {
            notasOutput.add(new DesempenhoProvaOutput(
                np.provaId().valor(),
                disciplinasPorProva.getOrDefault(np.provaId(), "Prova"),
                np.peso().valor(),
                np.acertos(),
                np.erros(),
                np.totalQuestoesConsideradas(),
                np.percentualAcertos().valor(),
                np.notaNaEscalaCem()
            ));
        }

        return new BoletimDesempenhoCandidatoOutput(
            candidatoAutenticadoId,
            meta.inscricaoId().valor(),
            meta.numeroInscricao(),
            concursoId,
            meta.cargoId().valor(),
            List.copyOf(notasOutput),
            resultado.notaTotalPonderada(),
            resultado.percentualTotalPonderado().valor(),
            resultado.totalAcertos(),
            resultado.totalErros(),
            resultado.situacao().name()
        );
    }

    @Override
    public GabaritoVigenciaOutput consultarGabaritoVigente(UUID provaId) {
        Objects.requireNonNull(provaId, "ProvaId obrigatorio.");
        return consultaMatrizPort.consultarGabaritoVigenteDaProva(new ProvaId(provaId));
    }
}
