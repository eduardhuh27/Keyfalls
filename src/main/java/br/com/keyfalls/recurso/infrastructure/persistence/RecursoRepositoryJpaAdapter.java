package br.com.keyfalls.recurso.infrastructure.persistence;

import br.com.keyfalls.recurso.application.HomologacaoConcursoPort;
import br.com.keyfalls.recurso.domain.DecisaoRecurso;
import br.com.keyfalls.recurso.domain.Recurso;
import br.com.keyfalls.recurso.domain.RecursoRepository;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RecursoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia PostgreSQL para o repositorio e porta ACL do contexto Recurso.
 * Implementa controle de concorrencia otimista (@Version) na tabela recurso.recursos e concurso.concursos.
 */
@Repository
public class RecursoRepositoryJpaAdapter implements RecursoRepository, HomologacaoConcursoPort {

    private final JdbcTemplate jdbcTemplate;

    public RecursoRepositoryJpaAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public void salvar(Recurso recurso) {
        Objects.requireNonNull(recurso, "Recurso obrigatorio.");
        Integer versaoAtual = jdbcTemplate.query(
            "SELECT version FROM recurso.recursos WHERE id = ?",
            rs -> rs.next() ? rs.getInt("version") : null,
            recurso.id().valor()
        );

        String statusBanco = recurso.foiJulgado() ? recurso.decisao().name() : "AGUARDANDO_JULGAMENTO";

        if (versaoAtual == null) {
            jdbcTemplate.update(
                """
                INSERT INTO recurso.recursos (
                    id, concurso_id, prova_id, questao_id, candidato_id,
                    fundamentacao, hash_agrupamento_argumentacao, status,
                    decisao, parecer_banca, protocolado_em, julgado_em, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                recurso.id().valor(),
                recurso.concursoId().valor(),
                recurso.provaId().valor(),
                recurso.questaoId().valor(),
                recurso.candidatoId().valor(),
                recurso.fundamentacao(),
                recurso.hashAgrupamentoArgumentacao(),
                statusBanco,
                recurso.decisao() != null ? recurso.decisao().name() : null,
                recurso.parecerBanca(),
                Timestamp.from(recurso.protocoladoEm()),
                recurso.julgadoEm() != null ? Timestamp.from(recurso.julgadoEm()) : null
            );
        } else {
            int updated = jdbcTemplate.update(
                """
                UPDATE recurso.recursos
                   SET status = ?,
                       decisao = ?,
                       parecer_banca = ?,
                       julgado_em = ?,
                       version = version + 1
                 WHERE id = ? AND version = ?
                """,
                statusBanco,
                recurso.decisao() != null ? recurso.decisao().name() : null,
                recurso.parecerBanca(),
                recurso.julgadoEm() != null ? Timestamp.from(recurso.julgadoEm()) : null,
                recurso.id().valor(),
                versaoAtual
            );
            if (updated == 0) {
                throw new OptimisticLockingFailureException(
                    "Conflito de concorrencia otimista ao julgar o recurso: " + recurso.id().valor()
                );
            }
        }
    }

    @Override
    public Optional<Recurso> buscarPorId(RecursoId id) {
        List<Recurso> lista = consultarRecursosPorWhere("id = ?", id.valor());
        return lista.stream().findFirst();
    }

    @Override
    public List<Recurso> listarPorQuestao(QuestaoId questaoId) {
        return consultarRecursosPorWhere("questao_id = ?", questaoId.valor());
    }

    @Override
    public List<Recurso> listarPorConcurso(ConcursoId concursoId) {
        return consultarRecursosPorWhere("concurso_id = ?", concursoId.valor());
    }

    @Override
    public long contarPendentesPorConcurso(ConcursoId concursoId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM recurso.recursos WHERE concurso_id = ? AND status = 'AGUARDANDO_JULGAMENTO'",
            Long.class,
            concursoId.valor()
        );
        return count != null ? count : 0L;
    }

    private List<Recurso> consultarRecursosPorWhere(String whereClause, UUID param) {
        return jdbcTemplate.query(
            """
            SELECT id, concurso_id, prova_id, questao_id, candidato_id,
                   fundamentacao, decisao, parecer_banca, protocolado_em, julgado_em
              FROM recurso.recursos
             WHERE %s
             ORDER BY protocolado_em ASC
            """.formatted(whereClause),
            (rs, rowNum) -> {
                String decStr = rs.getString("decisao");
                DecisaoRecurso decisao = (decStr != null && !decStr.isBlank()) ? DecisaoRecurso.valueOf(decStr) : null;
                Timestamp julgadoTs = rs.getTimestamp("julgado_em");
                return Recurso.reconstituir(
                    new RecursoId(rs.getObject("id", UUID.class)),
                    new ConcursoId(rs.getObject("concurso_id", UUID.class)),
                    new ProvaId(rs.getObject("prova_id", UUID.class)),
                    new QuestaoId(rs.getObject("questao_id", UUID.class)),
                    new CandidatoId(rs.getObject("candidato_id", UUID.class)),
                    rs.getString("fundamentacao"),
                    rs.getTimestamp("protocolado_em").toInstant(),
                    decisao,
                    rs.getString("parecer_banca"),
                    julgadoTs != null ? julgadoTs.toInstant() : null
                );
            },
            param
        );
    }

    @Override
    public void marcarQuestaoAnuladaNaMatriz(ProvaId provaId, QuestaoId questaoId) {
        jdbcTemplate.update(
            "UPDATE concurso.questoes SET anulada = TRUE WHERE id = ? AND prova_id = ?",
            questaoId.valor(),
            provaId.valor()
        );
    }

    @Override
    public Map<CargoId, Integer> encerrarConcursoEContarClassificadosPorCargo(
        ConcursoId concursoId,
        Instant homologadoEm
    ) {
        List<String> statusList = jdbcTemplate.query(
            "SELECT status FROM concurso.concursos WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> rs.getString("status"),
            concursoId.valor()
        );
        if (statusList.isEmpty()) {
            throw new RegraDeNegocioException("Concurso nao encontrado para homologacao: " + concursoId.valor());
        }
        if ("ENCERRADO_HOMOLOGADO".equals(statusList.getFirst())) {
            throw new RegraDeNegocioException("O resultado final deste concurso ja foi homologado e encerrado.");
        }

        jdbcTemplate.update(
            """
            UPDATE concurso.concursos
               SET status = 'ENCERRADO_HOMOLOGADO',
                   homologado_em = ?,
                   version = version + 1
             WHERE id = ?
            """,
            Timestamp.from(homologadoEm),
            concursoId.valor()
        );

        Map<CargoId, Integer> resultado = new LinkedHashMap<>();
        List<UUID> cargos = jdbcTemplate.query(
            "SELECT id FROM concurso.cargos WHERE concurso_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            concursoId.valor()
        );

        for (UUID cargoUuid : cargos) {
            Integer classificados = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                  FROM apuracao.resultados
                 WHERE concurso_id = ? AND cargo_id = ? AND situacao = 'CLASSIFICADO'
                """,
                Integer.class,
                concursoId.valor(),
                cargoUuid
            );
            resultado.put(new CargoId(cargoUuid), classificados != null ? classificados : 0);
        }

        return resultado;
    }
}
