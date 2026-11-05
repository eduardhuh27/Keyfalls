package br.com.keyfalls.concurso.infrastructure.persistence;

import br.com.keyfalls.concurso.domain.Cargo;
import br.com.keyfalls.concurso.domain.Concurso;
import br.com.keyfalls.concurso.domain.Concurso.Abrangencia;
import br.com.keyfalls.concurso.domain.ConcursoRepository;
import br.com.keyfalls.concurso.domain.Prova;
import br.com.keyfalls.concurso.domain.Questao;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Periodo;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de persistencia PostgreSQL real para a arvore completa do agregado Concurso
 * (concurso.concursos, concurso.cargos, concurso.provas e concurso.questoes).
 */
@Repository
public class ConcursoRepositoryJpaAdapter implements ConcursoRepository {

    private final JdbcTemplate jdbcTemplate;

    public ConcursoRepositoryJpaAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public void salvar(Concurso concurso) {
        String codigoPolitica = (concurso.politicaAnulacaoQuestao() instanceof PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy)
            ? "REDISTRIBUICAO_PESO_STRATEGY"
            : "PONTO_UNIVERSAL_STRATEGY";

        jdbcTemplate.update(
            """
            INSERT INTO concurso.concursos (
                id, codigo_edital, titulo, abrangencia, status,
                periodo_inscricao_inicio, periodo_inscricao_fim,
                periodo_isencao_inicio, periodo_isencao_fim, politica_anulacao
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                titulo = EXCLUDED.titulo,
                status = EXCLUDED.status,
                politica_anulacao = EXCLUDED.politica_anulacao,
                versao = concurso.concursos.versao + 1
            """,
            concurso.id().valor(),
            concurso.codigoEdital(),
            concurso.titulo(),
            concurso.abrangencia().name(),
            concurso.status().name(),
            Timestamp.from(concurso.periodoInscricao().inicio()),
            Timestamp.from(concurso.periodoInscricao().fim()),
            Timestamp.from(concurso.periodoIsencao().inicio()),
            Timestamp.from(concurso.periodoIsencao().fim()),
            codigoPolitica
        );

        for (Cargo cargo : concurso.cargos()) {
            jdbcTemplate.update(
                """
                INSERT INTO concurso.cargos (id, concurso_id, nome, vagas, taxa_inscricao)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    nome = EXCLUDED.nome,
                    vagas = EXCLUDED.vagas,
                    taxa_inscricao = EXCLUDED.taxa_inscricao
                """,
                cargo.id().valor(),
                concurso.id().valor(),
                cargo.nome(),
                cargo.vagas(),
                cargo.taxaInscricao()
            );
        }

        for (Prova prova : concurso.provas()) {
            boolean gabaritoDefinitivo = prova.gabarito() != null && prova.gabarito().isDefinitivo();
            Timestamp gabaritoEm = prova.gabarito() != null ? Timestamp.from(Instant.now()) : null;

            jdbcTemplate.update(
                """
                INSERT INTO concurso.provas (
                    id, concurso_id, cargo_id, disciplina, peso, gabarito_definitivo, gabarito_publicado_em
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    gabarito_definitivo = EXCLUDED.gabarito_definitivo,
                    gabarito_publicado_em = COALESCE(concurso.provas.gabarito_publicado_em, EXCLUDED.gabarito_publicado_em)
                """,
                prova.id().valor(),
                concurso.id().valor(),
                prova.cargoId().valor(),
                prova.disciplina(),
                prova.peso().valor(),
                gabaritoDefinitivo,
                gabaritoEm
            );

            for (Questao questao : prova.questoes()) {
                Alternativa respOficial = prova.gabarito() != null
                    ? prova.gabarito().respostasOficiais().get(questao.id())
                    : null;

                jdbcTemplate.update(
                    """
                    INSERT INTO concurso.questoes (
                        id, prova_id, numero_ordem, enunciado, alternativas_json,
                        resposta_oficial, anulada, motivo_anulacao
                    ) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET
                        resposta_oficial = EXCLUDED.resposta_oficial,
                        anulada = EXCLUDED.anulada,
                        motivo_anulacao = EXCLUDED.motivo_anulacao
                    """,
                    questao.id().valor(),
                    prova.id().valor(),
                    questao.numeroOrdem(),
                    questao.enunciado(),
                    serializarOpcoesJson(questao.opcoes()),
                    respOficial != null ? respOficial.name() : null,
                    questao.isAnulada(),
                    questao.justificativaAnulacao()
                );
            }
        }
    }

    @Override
    public Optional<Concurso> buscarPorId(ConcursoId id) {
        List<Concurso> lista = jdbcTemplate.query(
            """
            SELECT id, codigo_edital, titulo, abrangencia, status,
                   periodo_inscricao_inicio, periodo_inscricao_fim,
                   periodo_isencao_inicio, periodo_isencao_fim, politica_anulacao
            FROM concurso.concursos WHERE id = ?
            """,
            (rs, rowNum) -> {
                PoliticaAnulacaoQuestao politica = "REDISTRIBUICAO_PESO_STRATEGY".equals(rs.getString("politica_anulacao"))
                    || "DESCONSIDERAR_DO_TOTAL_DA_PROVA".equals(rs.getString("politica_anulacao"))
                    ? new PoliticaAnulacaoQuestao.RedistribuicaoPesoStrategy()
                    : new PoliticaAnulacaoQuestao.PontoUniversalStrategy();

                return Concurso.abrirEdital(
                    new ConcursoId(UUID.fromString(rs.getString("id"))),
                    rs.getString("codigo_edital"),
                    rs.getString("titulo"),
                    Abrangencia.valueOf(rs.getString("abrangencia")),
                    new Periodo(
                        rs.getTimestamp("periodo_inscricao_inicio").toInstant(),
                        rs.getTimestamp("periodo_inscricao_fim").toInstant()
                    ),
                    new Periodo(
                        rs.getTimestamp("periodo_isencao_inicio").toInstant(),
                        rs.getTimestamp("periodo_isencao_fim").toInstant()
                    ),
                    politica
                );
            },
            id.valor()
        );

        if (lista.isEmpty()) {
            return Optional.empty();
        }

        Concurso concurso = lista.get(0);
        carregarCargosProvasEQuestoes(concurso);
        concurso.pullDomainEvents(); // Limpa eventos disparados apenas pela hidratacao
        return Optional.of(concurso);
    }

    @Override
    public Optional<Concurso> buscarPorProvaId(ProvaId provaId) {
        List<UUID> concursoIds = jdbcTemplate.query(
            "SELECT concurso_id FROM concurso.provas WHERE id = ?",
            (rs, rowNum) -> UUID.fromString(rs.getString("concurso_id")),
            provaId.valor()
        );
        if (concursoIds.isEmpty()) {
            return Optional.empty();
        }
        return buscarPorId(new ConcursoId(concursoIds.get(0)));
    }

    private void carregarCargosProvasEQuestoes(Concurso concurso) {
        jdbcTemplate.query(
            "SELECT id, nome, vagas, taxa_inscricao FROM concurso.cargos WHERE concurso_id = ?",
            rs -> {
                concurso.adicionarCargo(
                    new CargoId(UUID.fromString(rs.getString("id"))),
                    rs.getString("nome"),
                    rs.getInt("vagas"),
                    rs.getBigDecimal("taxa_inscricao")
                );
            },
            concurso.id().valor()
        );

        jdbcTemplate.query(
            "SELECT id, cargo_id, disciplina, peso, gabarito_definitivo FROM concurso.provas WHERE concurso_id = ?",
            rs -> {
                ProvaId pId = new ProvaId(UUID.fromString(rs.getString("id")));
                CargoId cId = new CargoId(UUID.fromString(rs.getString("cargo_id")));
                concurso.configurarProva(pId, cId, rs.getString("disciplina"), new Peso(rs.getBigDecimal("peso")));

                Map<QuestaoId, Alternativa> gabaritoCarregado = new LinkedHashMap<>();
                jdbcTemplate.query(
                    """
                    SELECT id, numero_ordem, enunciado, resposta_oficial, anulada, motivo_anulacao
                    FROM concurso.questoes WHERE prova_id = ? ORDER BY numero_ordem ASC
                    """,
                    qRs -> {
                        QuestaoId qId = new QuestaoId(UUID.fromString(qRs.getString("id")));
                        Map<Alternativa, String> opcoesPadrao = new EnumMap<>(Alternativa.class);
                        for (Alternativa alt : Alternativa.values()) {
                            opcoesPadrao.put(alt, "Opcao " + alt.name());
                        }
                        concurso.incluirQuestaoNaProva(
                            pId, qId, qRs.getInt("numero_ordem"), qRs.getString("enunciado"), opcoesPadrao
                        );
                        String resp = qRs.getString("resposta_oficial");
                        if (resp != null && !resp.isBlank()) {
                            gabaritoCarregado.put(qId, Alternativa.valueOf(resp.trim()));
                        }
                        if (qRs.getBoolean("anulada")) {
                            concurso.anularQuestao(
                                pId, qId,
                                Objects.requireNonNullElse(qRs.getString("motivo_anulacao"), "Anulada por recurso"),
                                Instant.now()
                            );
                        }
                    },
                    pId.valor()
                );

                if (!gabaritoCarregado.isEmpty()) {
                    concurso.publicarGabaritoPreliminar(pId, gabaritoCarregado, Instant.now());
                    if (rs.getBoolean("gabarito_definitivo")) {
                        concurso.consolidarGabaritoDefinitivo(pId, Instant.now());
                    }
                }
            },
            concurso.id().valor()
        );
    }

    private static String serializarOpcoesJson(Map<Alternativa, String> opcoes) {
        StringBuilder sb = new StringBuilder("{");
        boolean primeiro = true;
        for (Map.Entry<Alternativa, String> entry : opcoes.entrySet()) {
            if (!primeiro) sb.append(",");
            primeiro = false;
            String escapado = entry.getValue().replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append("\"").append(entry.getKey().name()).append("\":\"").append(escapado).append("\"");
        }
        return sb.append("}").toString();
    }
}
