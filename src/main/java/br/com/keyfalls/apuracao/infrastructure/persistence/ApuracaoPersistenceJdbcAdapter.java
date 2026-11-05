package br.com.keyfalls.apuracao.infrastructure.persistence;

import br.com.keyfalls.apuracao.application.ApuracaoUseCases;
import br.com.keyfalls.apuracao.application.ConsultaMatrizApuracaoPort;
import br.com.keyfalls.apuracao.application.ControleAnulacaoProcessadaPort;
import br.com.keyfalls.apuracao.domain.CalculadoraDeNota;
import br.com.keyfalls.apuracao.domain.FichaDeResposta;
import br.com.keyfalls.apuracao.domain.FichaDeRespostaRepository;
import br.com.keyfalls.apuracao.domain.NotaProva;
import br.com.keyfalls.apuracao.domain.Resultado;
import br.com.keyfalls.apuracao.domain.ResultadoRepository;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.FichaDeRespostaId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.ResultadoId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia PostgreSQL para os repositorios e portas ACL do contexto Apuracao.
 */
@Repository
public class ApuracaoPersistenceJdbcAdapter
    implements FichaDeRespostaRepository, ResultadoRepository, ConsultaMatrizApuracaoPort, ControleAnulacaoProcessadaPort {

    private final JdbcTemplate jdbcTemplate;

    public ApuracaoPersistenceJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public void salvar(FichaDeResposta ficha) {
        Objects.requireNonNull(ficha, "FichaDeResposta obrigatoria.");
        jdbcTemplate.update(
            """
            INSERT INTO apuracao.fichas_de_resposta (id, inscricao_id, concurso_id, capturada_em)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (inscricao_id) DO UPDATE SET
                capturada_em = EXCLUDED.capturada_em
            """,
            ficha.id().valor(),
            ficha.inscricaoId().valor(),
            ficha.concursoId().valor(),
            Timestamp.from(ficha.capturadaEm())
        );

        UUID idEfetivo = jdbcTemplate.queryForObject(
            "SELECT id FROM apuracao.fichas_de_resposta WHERE inscricao_id = ?",
            UUID.class,
            ficha.inscricaoId().valor()
        );

        jdbcTemplate.update("DELETE FROM apuracao.respostas_marcadas WHERE ficha_id = ?", idEfetivo);

        ficha.respostasPorProva().forEach((provaId, marcacoes) ->
            marcacoes.forEach((questaoId, alternativa) ->
                jdbcTemplate.update(
                    """
                    INSERT INTO apuracao.respostas_marcadas (ficha_id, prova_id, questao_id, alternativa_marcada)
                    VALUES (?, ?, ?, ?)
                    """,
                    idEfetivo,
                    provaId.valor(),
                    questaoId.valor(),
                    alternativa != null ? alternativa.name() : null
                )
            )
        );
    }

    @Override
    public Optional<FichaDeResposta> buscarPorId(FichaDeRespostaId id) {
        return buscarFichaPorWhere("id = ?", id.valor());
    }

    @Override
    public Optional<FichaDeResposta> buscarPorInscricao(InscricaoId inscricaoId) {
        return buscarFichaPorWhere("inscricao_id = ?", inscricaoId.valor());
    }

    @Override
    public List<FichaDeResposta> listarPorConcurso(ConcursoId concursoId) {
        List<UUID> ids = jdbcTemplate.query(
            "SELECT id FROM apuracao.fichas_de_resposta WHERE concurso_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            concursoId.valor()
        );
        return ids.stream()
            .map(id -> buscarPorId(new FichaDeRespostaId(id)).orElseThrow())
            .toList();
    }

    private Optional<FichaDeResposta> buscarFichaPorWhere(String whereClause, UUID param) {
        List<FichaRow> rows = jdbcTemplate.query(
            "SELECT id, inscricao_id, concurso_id, capturada_em FROM apuracao.fichas_de_resposta WHERE " + whereClause,
            (rs, rowNum) -> new FichaRow(
                rs.getObject("id", UUID.class),
                rs.getObject("inscricao_id", UUID.class),
                rs.getObject("concurso_id", UUID.class),
                rs.getTimestamp("capturada_em").toInstant()
            ),
            param
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        FichaRow row = rows.getFirst();
        Map<ProvaId, Map<QuestaoId, Alternativa>> respostasPorProva = new LinkedHashMap<>();

        jdbcTemplate.query(
            "SELECT prova_id, questao_id, alternativa_marcada FROM apuracao.respostas_marcadas WHERE ficha_id = ?",
            rs -> {
                ProvaId provaId = new ProvaId(rs.getObject("prova_id", UUID.class));
                QuestaoId questaoId = new QuestaoId(rs.getObject("questao_id", UUID.class));
                String altStr = rs.getString("alternativa_marcada");
                Alternativa alt = (altStr != null && !altStr.isBlank()) ? Alternativa.valueOf(altStr) : null;
                respostasPorProva
                    .computeIfAbsent(provaId, k -> new LinkedHashMap<>())
                    .put(questaoId, alt);
            },
            row.id()
        );

        return Optional.of(FichaDeResposta.capturar(
            new FichaDeRespostaId(row.id()),
            new InscricaoId(row.inscricaoId()),
            new ConcursoId(row.concursoId()),
            row.capturadaEm(),
            respostasPorProva
        ));
    }

    private record FichaRow(UUID id, UUID inscricaoId, UUID concursoId, Instant capturadaEm) {}

    @Override
    public void salvar(Resultado resultado) {
        Objects.requireNonNull(resultado, "Resultado obrigatorio.");
        jdbcTemplate.update(
            """
            INSERT INTO apuracao.resultados (
                id, inscricao_id, concurso_id, cargo_id, nota_total_ponderada,
                percentual_total_ponderado, total_acertos, total_erros, situacao, calculado_em
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (inscricao_id) DO UPDATE SET
                nota_total_ponderada = EXCLUDED.nota_total_ponderada,
                percentual_total_ponderado = EXCLUDED.percentual_total_ponderado,
                total_acertos = EXCLUDED.total_acertos,
                total_erros = EXCLUDED.total_erros,
                situacao = EXCLUDED.situacao,
                calculado_em = EXCLUDED.calculado_em
            """,
            resultado.id().valor(),
            resultado.inscricaoId().valor(),
            resultado.concursoId().valor(),
            resultado.cargoId().valor(),
            resultado.notaTotalPonderada(),
            resultado.percentualTotalPonderado().valor(),
            resultado.totalAcertos(),
            resultado.totalErros(),
            resultado.situacao().name(),
            Timestamp.from(Instant.now())
        );

        UUID idEfetivo = jdbcTemplate.queryForObject(
            "SELECT id FROM apuracao.resultados WHERE inscricao_id = ?",
            UUID.class,
            resultado.inscricaoId().valor()
        );

        jdbcTemplate.update("DELETE FROM apuracao.notas_prova WHERE resultado_id = ?", idEfetivo);

        for (NotaProva np : resultado.notasPorProva()) {
            jdbcTemplate.update(
                """
                INSERT INTO apuracao.notas_prova (
                    resultado_id, prova_id, peso, acertos, erros, total_questoes_consideradas,
                    percentual_acertos, nota_final_prova
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                idEfetivo,
                np.provaId().valor(),
                np.peso().valor(),
                np.acertos(),
                np.erros(),
                np.totalQuestoesConsideradas(),
                np.percentualAcertos().valor(),
                np.notaNaEscalaCem()
            );
        }
    }

    @Override
    public void salvarTodos(List<Resultado> resultados) {
        for (Resultado r : resultados) {
            salvar(r);
        }
    }

    @Override
    public Optional<Resultado> buscarPorId(ResultadoId id) {
        return buscarResultadoPorWhere("id = ?", id.valor());
    }

    @Override
    public Optional<Resultado> buscarPorInscricao(InscricaoId inscricaoId) {
        return buscarResultadoPorWhere("inscricao_id = ?", inscricaoId.valor());
    }

    @Override
    public List<Resultado> listarPorConcursoECargo(ConcursoId concursoId, CargoId cargoId) {
        List<UUID> ids = jdbcTemplate.query(
            "SELECT id FROM apuracao.resultados WHERE concurso_id = ? AND cargo_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            concursoId.valor(),
            cargoId.valor()
        );
        return ids.stream()
            .map(id -> buscarPorId(new ResultadoId(id)).orElseThrow())
            .toList();
    }

    private Optional<Resultado> buscarResultadoPorWhere(String whereClause, UUID param) {
        List<ResultadoRow> rows = jdbcTemplate.query(
            "SELECT id, inscricao_id, concurso_id, cargo_id FROM apuracao.resultados WHERE " + whereClause,
            (rs, rowNum) -> new ResultadoRow(
                rs.getObject("id", UUID.class),
                rs.getObject("inscricao_id", UUID.class),
                rs.getObject("concurso_id", UUID.class),
                rs.getObject("cargo_id", UUID.class)
            ),
            param
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ResultadoRow row = rows.getFirst();
        List<NotaProva> notasProva = jdbcTemplate.query(
            """
            SELECT prova_id, peso, acertos, erros, total_questoes_consideradas
              FROM apuracao.notas_prova
             WHERE resultado_id = ?
            """,
            (rs, rowNum) -> NotaProva.criar(
                new ProvaId(rs.getObject("prova_id", UUID.class)),
                new Peso(rs.getBigDecimal("peso")),
                rs.getInt("acertos"),
                rs.getInt("erros"),
                rs.getInt("total_questoes_consideradas")
            ),
            row.id()
        );

        return Optional.of(Resultado.consolidar(
            new ResultadoId(row.id()),
            new InscricaoId(row.inscricaoId()),
            new ConcursoId(row.concursoId()),
            new CargoId(row.cargoId()),
            notasProva
        ));
    }

    private record ResultadoRow(UUID id, UUID inscricaoId, UUID concursoId, UUID cargoId) {}

    @Override
    public ContextoRecalculoCargo obterContextoAtualizadoPorProva(ConcursoId concursoId, ProvaId provaId) {
        UUID cargoUuid = jdbcTemplate.queryForObject(
            "SELECT cargo_id FROM concurso.provas WHERE id = ?",
            UUID.class,
            provaId.valor()
        );
        return montarContextoCargo(concursoId, new CargoId(cargoUuid));
    }

    @Override
    public List<ContextoRecalculoCargo> listarContextosDoConcurso(ConcursoId concursoId) {
        List<UUID> cargosIds = jdbcTemplate.query(
            "SELECT id FROM concurso.cargos WHERE concurso_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            concursoId.valor()
        );
        return cargosIds.stream()
            .map(cid -> montarContextoCargo(concursoId, new CargoId(cid)))
            .toList();
    }

    private ContextoRecalculoCargo montarContextoCargo(ConcursoId concursoId, CargoId cargoId) {
        String polStr = jdbcTemplate.queryForObject(
            "SELECT politica_anulacao FROM concurso.concursos WHERE id = ?",
            String.class,
            concursoId.valor()
        );
        PoliticaAnulacaoQuestao politica = PoliticaAnulacaoQuestao.valueOf(polStr);

        List<CalculadoraDeNota.MatrizGabaritoProva> matrizes = jdbcTemplate.query(
            "SELECT id, peso FROM concurso.provas WHERE cargo_id = ?",
            (rs, rowNum) -> {
                ProvaId provaId = new ProvaId(rs.getObject("id", UUID.class));
                Peso peso = new Peso(rs.getBigDecimal("peso"));
                List<CalculadoraDeNota.ItemMatrizQuestao> itens = jdbcTemplate.query(
                    """
                    SELECT id, gabarito_oficial, anulada
                      FROM concurso.questoes
                     WHERE prova_id = ?
                     ORDER BY numero_ordem ASC
                    """,
                    (rq, idx) -> {
                        String gab = rq.getString("gabarito_oficial");
                        return new CalculadoraDeNota.ItemMatrizQuestao(
                            new QuestaoId(rq.getObject("id", UUID.class)),
                            gab != null ? Alternativa.valueOf(gab) : Alternativa.A,
                            rq.getBoolean("anulada")
                        );
                    },
                    provaId.valor()
                );
                return new CalculadoraDeNota.MatrizGabaritoProva(provaId, peso, itens);
            },
            cargoId.valor()
        );

        return new ContextoRecalculoCargo(cargoId, politica, matrizes);
    }

    @Override
    public Map<InscricaoId, MetadadosCandidatoInscricao> buscarMetadadosInscricoesDoConcurso(ConcursoId concursoId) {
        Map<InscricaoId, MetadadosCandidatoInscricao> mapa = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
            SELECT i.id AS inscricao_id, i.candidato_id, i.numero_inscricao, i.cargo_id,
                   COALESCE(c.nome_completo, 'Candidato') AS nome_completo
              FROM inscricao.inscricoes i
              LEFT JOIN identidade.candidatos c ON c.id = i.candidato_id
             WHERE i.concurso_id = ?
            """,
            rs -> {
                InscricaoId inscId = new InscricaoId(rs.getObject("inscricao_id", UUID.class));
                mapa.put(inscId, new MetadadosCandidatoInscricao(
                    inscId,
                    rs.getObject("candidato_id", UUID.class),
                    rs.getString("numero_inscricao"),
                    rs.getString("nome_completo"),
                    new CargoId(rs.getObject("cargo_id", UUID.class))
                ));
            },
            concursoId.valor()
        );
        return mapa;
    }

    @Override
    public Optional<MetadadosCandidatoInscricao> buscarInscricaoDoCandidatoNoConcurso(
        UUID candidatoId,
        ConcursoId concursoId
    ) {
        List<MetadadosCandidatoInscricao> lista = jdbcTemplate.query(
            """
            SELECT i.id AS inscricao_id, i.candidato_id, i.numero_inscricao, i.cargo_id,
                   COALESCE(c.nome_completo, 'Candidato') AS nome_completo
              FROM inscricao.inscricoes i
              LEFT JOIN identidade.candidatos c ON c.id = i.candidato_id
             WHERE i.candidato_id = ? AND i.concurso_id = ?
            """,
            (rs, rowNum) -> new MetadadosCandidatoInscricao(
                new InscricaoId(rs.getObject("inscricao_id", UUID.class)),
                rs.getObject("candidato_id", UUID.class),
                rs.getString("numero_inscricao"),
                rs.getString("nome_completo"),
                new CargoId(rs.getObject("cargo_id", UUID.class))
            ),
            candidatoId,
            concursoId.valor()
        );
        return lista.stream().findFirst();
    }

    @Override
    public Map<ProvaId, String> mapearDisciplinaPorProvaDoConcurso(ConcursoId concursoId) {
        Map<ProvaId, String> mapa = new LinkedHashMap<>();
        jdbcTemplate.query(
            """
            SELECT p.id, p.disciplina
              FROM concurso.provas p
              JOIN concurso.cargos cg ON cg.id = p.cargo_id
             WHERE cg.concurso_id = ?
            """,
            rs -> mapa.put(new ProvaId(rs.getObject("id", UUID.class)), rs.getString("disciplina")),
            concursoId.valor()
        );
        return mapa;
    }

    @Override
    public ApuracaoUseCases.GabaritoVigenciaOutput consultarGabaritoVigenteDaProva(ProvaId provaId) {
        List<Boolean> defList = jdbcTemplate.query(
            "SELECT gabarito_definitivo FROM concurso.provas WHERE id = ?",
            (rs, rowNum) -> rs.getBoolean("gabarito_definitivo"),
            provaId.valor()
        );
        if (defList.isEmpty()) {
            throw new RegraDeNegocioException("Prova nao encontrada para consulta de gabarito: " + provaId.valor());
        }

        List<ApuracaoUseCases.ItemGabaritoVigenciaOutput> itens = jdbcTemplate.query(
            """
            SELECT numero_ordem, id, gabarito_oficial, anulada
              FROM concurso.questoes
             WHERE prova_id = ?
             ORDER BY numero_ordem ASC
            """,
            (rs, rowNum) -> {
                String gab = rs.getString("gabarito_oficial");
                return new ApuracaoUseCases.ItemGabaritoVigenciaOutput(
                    rs.getInt("numero_ordem"),
                    rs.getObject("id", UUID.class),
                    gab != null ? Alternativa.valueOf(gab) : null,
                    rs.getBoolean("anulada")
                );
            },
            provaId.valor()
        );

        return new ApuracaoUseCases.GabaritoVigenciaOutput(provaId.valor(), defList.getFirst(), itens);
    }

    @Override
    public boolean anulacaoJaAplicada(ProvaId provaId, QuestaoId questaoId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM apuracao.anulacoes_processadas WHERE prova_id = ? AND questao_id = ?",
            Integer.class,
            provaId.valor(),
            questaoId.valor()
        );
        return count != null && count > 0;
    }

    @Override
    public void registrarAnulacaoAplicada(ProvaId provaId, QuestaoId questaoId, UUID eventIdOrigem, Instant aplicadoEm) {
        jdbcTemplate.update(
            """
            INSERT INTO apuracao.anulacoes_processadas (prova_id, questao_id, event_id_origem, aplicado_em)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (prova_id, questao_id) DO NOTHING
            """,
            provaId.valor(),
            questaoId.valor(),
            eventIdOrigem,
            Timestamp.from(aplicadoEm)
        );
    }
}
