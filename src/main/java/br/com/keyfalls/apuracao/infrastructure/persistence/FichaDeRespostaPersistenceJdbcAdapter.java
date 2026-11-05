package br.com.keyfalls.apuracao.infrastructure.persistence;

import br.com.keyfalls.apuracao.domain.FichaDeResposta;
import br.com.keyfalls.apuracao.domain.FichaDeRespostaRepository;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.FichaDeRespostaId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
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
 * Adaptador de persistencia PostgreSQL para o agregado FichaDeResposta do contexto Apuracao.
 * Separado de {@link ApuracaoPersistenceJdbcAdapter} porque FichaDeRespostaRepository e ResultadoRepository
 * declaram {@code buscarPorInscricao(InscricaoId)} com tipos de retorno distintos, o que impede
 * que uma unica classe implemente ambos.
 */
@Repository
public class FichaDeRespostaPersistenceJdbcAdapter implements FichaDeRespostaRepository {

    private final JdbcTemplate jdbcTemplate;

    public FichaDeRespostaPersistenceJdbcAdapter(JdbcTemplate jdbcTemplate) {
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
}
