package br.com.keyfalls.comunicacao.infrastructure.persistence;

import br.com.keyfalls.comunicacao.application.ConsultaDestinatarioPort;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.SalaId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/**
 * Adaptador PostgreSQL para a porta ACL ConsultaDestinatarioPort do contexto Comunicacao.
 */
@Repository
public class ConsultaDestinatarioJdbcAdapter implements ConsultaDestinatarioPort {

    private final JdbcTemplate jdbcTemplate;

    public ConsultaDestinatarioJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public DadosContatoCandidato buscarPorCandidatoId(CandidatoId candidatoId) {
        List<DadosContatoCandidato> lista = jdbcTemplate.query(
            "SELECT nome_completo, email FROM identidade.usuarios WHERE id = ?",
            (rs, rowNum) -> new DadosContatoCandidato(
                rs.getString("nome_completo"),
                new Email(rs.getString("email"))
            ),
            candidatoId.valor()
        );
        return lista.stream()
            .findFirst()
            .orElseThrow(() -> new RegraDeNegocioException(
                "Destinatario nao encontrado para o candidato: " + candidatoId.valor()
            ));
    }

    @Override
    public DadosContatoCandidato buscarPorInscricaoId(InscricaoId inscricaoId) {
        List<DadosContatoCandidato> lista = jdbcTemplate.query(
            """
            SELECT c.nome_completo, c.email
              FROM inscricao.inscricoes i
              JOIN identidade.usuarios c ON c.id = i.candidato_id
             WHERE i.id = ?
            """,
            (rs, rowNum) -> new DadosContatoCandidato(
                rs.getString("nome_completo"),
                new Email(rs.getString("email"))
            ),
            inscricaoId.valor()
        );
        return lista.stream()
            .findFirst()
            .orElseThrow(() -> new RegraDeNegocioException(
                "Destinatario nao encontrado para a inscricao: " + inscricaoId.valor()
            ));
    }

    @Override
    public DadosLocalAlocado buscarDetalhesSala(LocalDeProvaId localDeProvaId, SalaId salaId) {
        List<DadosLocalAlocado> lista = jdbcTemplate.query(
            """
            SELECT l.nome_instituicao, s.identificacao,
                   CONCAT(l.logradouro, ', ', l.numero, ' - ', l.bairro, ', ', l.cidade, '/', l.uf) AS endereco_fmt
              FROM logistica.locais_de_prova l
              JOIN logistica.salas s ON s.local_de_prova_id = l.id
             WHERE l.id = ? AND s.id = ?
            """,
            (rs, rowNum) -> new DadosLocalAlocado(
                rs.getString("nome_instituicao"),
                rs.getString("identificacao"),
                rs.getString("endereco_fmt")
            ),
            localDeProvaId.valor(),
            salaId.valor()
        );
        return lista.stream()
            .findFirst()
            .orElseThrow(() -> new RegraDeNegocioException("Detalhes de local/sala nao encontrados."));
    }

    @Override
    public List<DadosContatoCandidato> listarCandidatosHomologadosDoCargo(ConcursoId concursoId, CargoId cargoId) {
        return jdbcTemplate.query(
            """
            SELECT c.nome_completo, c.email
              FROM inscricao.inscricoes i
              JOIN identidade.usuarios c ON c.id = i.candidato_id
             WHERE i.concurso_id = ?
               AND i.cargo_id = ?
               AND i.status IN ('HOMOLOGADA', 'PAGA', 'ISENTA')
            """,
            (rs, rowNum) -> new DadosContatoCandidato(
                rs.getString("nome_completo"),
                new Email(rs.getString("email"))
            ),
            concursoId.valor(),
            cargoId.valor()
        );
    }
}
