package br.com.keyfalls.inscricao.infrastructure.persistence;

import br.com.keyfalls.inscricao.application.ConsultaOfertaConcursoPort;
import br.com.keyfalls.inscricao.application.GeradorNumeroInscricaoPort;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.Periodo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adapter ACL na infraestrutura de Inscricao para consultar a oferta do cargo e gerar
 * numeros sequenciais de inscricao sem acoplamento de codigo ao contexto Concurso.
 */
@Component
public class InscricaoOfertaENumeroJdbcAdapter implements ConsultaOfertaConcursoPort, GeradorNumeroInscricaoPort {

    private static final AtomicLong SEQUENCIAL_ATOMICO = new AtomicLong(100000L);

    private final JdbcTemplate jdbcTemplate;

    public InscricaoOfertaENumeroJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public Optional<DadosOfertaCargo> consultarOferta(ConcursoId concursoId, CargoId cargoId) {
        List<DadosOfertaCargo> lista = jdbcTemplate.query(
            """
            SELECT c.periodo_inscricao_inicio, c.periodo_inscricao_fim,
                   c.periodo_isencao_inicio, c.periodo_isencao_fim,
                   cg.taxa_inscricao
            FROM concurso.concursos c
            JOIN concurso.cargos cg ON cg.concurso_id = c.id
            WHERE c.id = ? AND cg.id = ?
            """,
            (rs, rowNum) -> {
                var pInscricao = new Periodo(
                    rs.getTimestamp("periodo_inscricao_inicio").toInstant(),
                    rs.getTimestamp("periodo_inscricao_fim").toInstant()
                );
                var pIsencao = new Periodo(
                    rs.getTimestamp("periodo_isencao_inicio").toInstant(),
                    rs.getTimestamp("periodo_isencao_fim").toInstant()
                );
                LocalDate vencimento = LocalDate.ofInstant(pInscricao.fim(), ZoneOffset.UTC).plusDays(5);
                return new DadosOfertaCargo(pInscricao, pIsencao, rs.getBigDecimal("taxa_inscricao"), vencimento);
            },
            concursoId.valor(),
            cargoId.valor()
        );
        return lista.stream().findFirst();
    }

    @Override
    public NumeroInscricao proximoNumero(ConcursoId concursoId) {
        int ano = LocalDate.now(ZoneOffset.UTC).getYear();
        long proximo = SEQUENCIAL_ATOMICO.incrementAndGet();
        return new NumeroInscricao("%04d%06d".formatted(ano, proximo));
    }
}
