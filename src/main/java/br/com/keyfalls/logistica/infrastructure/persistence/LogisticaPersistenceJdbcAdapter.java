package br.com.keyfalls.logistica.infrastructure.persistence;

import br.com.keyfalls.logistica.application.ConsultaCandidatosHomologadosPort;
import br.com.keyfalls.logistica.domain.AlocacaoDeSala;
import br.com.keyfalls.logistica.domain.AlocacaoDeSalaRepository;
import br.com.keyfalls.logistica.domain.AlocadorDeSalas.CandidatoHomologadoParaAlocacao;
import br.com.keyfalls.logistica.domain.Fiscal;
import br.com.keyfalls.logistica.domain.ListaDePresenca;
import br.com.keyfalls.logistica.domain.LocalDeProva;
import br.com.keyfalls.logistica.domain.LocalDeProvaRepository;
import br.com.keyfalls.logistica.domain.Sala;
import br.com.keyfalls.shared.domain.AlocacaoDeSalaId;
import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.SalaId;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador de persistencia PostgreSQL para os repositorios e portas de consulta do contexto Logistica.
 * Garante controle de concorrencia otimista (version) nas salas durante alocacao simultanea.
 */
@Repository
public class LogisticaPersistenceJdbcAdapter
    implements LocalDeProvaRepository, AlocacaoDeSalaRepository, ConsultaCandidatosHomologadosPort {

    private final JdbcTemplate jdbcTemplate;

    public LogisticaPersistenceJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public void salvar(LocalDeProva local) {
        Objects.requireNonNull(local, "LocalDeProva obrigatorio.");
        Endereco end = local.endereco();
        Double lat = end.latitude();
        Double lon = end.longitude();

        jdbcTemplate.update(
            """
            INSERT INTO logistica.locais_de_prova (
                id, concurso_id, nome_instituicao, logradouro, numero, bairro, cidade, uf, cep, latitude, longitude, versao
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT (id) DO UPDATE SET
                nome_instituicao = EXCLUDED.nome_instituicao,
                versao = logistica.locais_de_prova.versao + 1
            """,
            local.id().valor(),
            local.concursoId().valor(),
            local.nomeInstituicao(),
            end.logradouro(),
            end.numero(),
            end.bairro(),
            end.cidade(),
            end.uf(),
            end.cep(),
            lat,
            lon
        );

        for (Sala sala : local.salas()) {
            Integer versaoAtual = jdbcTemplate.query(
                "SELECT versao FROM logistica.salas WHERE id = ?",
                rs -> rs.next() ? rs.getInt("versao") : null,
                sala.id().valor()
            );

            if (versaoAtual == null) {
                jdbcTemplate.update(
                    """
                    INSERT INTO logistica.salas (
                        id, local_de_prova_id, identificacao, capacidade_maxima, vagas_ocupadas,
                        cargo_atendido_id, nome_fiscal, cpf_fiscal, versao
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    sala.id().valor(),
                    local.id().valor(),
                    sala.identificacao(),
                    sala.capacidadeMaxima(),
                    sala.inscricoesAlocadas().size(),
                    sala.cargoAtendido().valor(),
                    sala.fiscal().nomeCompleto(),
                    sala.fiscal().cpf().numero()
                );
            } else {
                int updated = jdbcTemplate.update(
                    """
                    UPDATE logistica.salas
                       SET vagas_ocupadas = ?,
                           nome_fiscal = ?,
                           cpf_fiscal = ?,
                           versao = versao + 1
                     WHERE id = ? AND versao = ?
                    """,
                    sala.inscricoesAlocadas().size(),
                    sala.fiscal().nomeCompleto(),
                    sala.fiscal().cpf().numero(),
                    sala.id().valor(),
                    versaoAtual
                );
                if (updated == 0) {
                    throw new OptimisticLockingFailureException(
                        "Conflito de concorrencia otimista (@Version) ao alocar vaga na sala " + sala.id().valor()
                    );
                }
            }
        }
    }

    @Override
    public Optional<LocalDeProva> buscarPorId(LocalDeProvaId id) {
        List<LocalDeProva> lista = jdbcTemplate.query(
            """
            SELECT id, concurso_id, nome_instituicao, logradouro, numero, bairro, cidade, uf, cep, latitude, longitude
              FROM logistica.locais_de_prova
             WHERE id = ?
            """,
            (rs, rowNum) -> {
                Endereco endereco = new Endereco(
                    rs.getString("logradouro"),
                    rs.getString("numero"),
                    rs.getString("bairro"),
                    rs.getString("cidade"),
                    rs.getString("uf"),
                    rs.getString("cep"),
                    rs.getObject("latitude", Double.class),
                    rs.getObject("longitude", Double.class)
                );
                return LocalDeProva.credenciar(
                    new LocalDeProvaId(rs.getObject("id", UUID.class)),
                    new ConcursoId(rs.getObject("concurso_id", UUID.class)),
                    rs.getString("nome_instituicao"),
                    endereco
                );
            },
            id.valor()
        );
        if (lista.isEmpty()) {
            return Optional.empty();
        }
        LocalDeProva local = lista.getFirst();
        carregarSalasDoLocal(local);
        return Optional.of(local);
    }

    @Override
    public List<LocalDeProva> listarPorConcurso(ConcursoId concursoId) {
        List<UUID> ids = jdbcTemplate.query(
            "SELECT id FROM logistica.locais_de_prova WHERE concurso_id = ? ORDER BY nome_instituicao",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            concursoId.valor()
        );
        return ids.stream()
            .map(id -> buscarPorId(new LocalDeProvaId(id)).orElseThrow())
            .toList();
    }

    private void carregarSalasDoLocal(LocalDeProva local) {
        jdbcTemplate.query(
            """
            SELECT id, identificacao, capacidade_maxima, cargo_atendido_id, nome_fiscal, cpf_fiscal
              FROM logistica.salas
             WHERE local_de_prova_id = ?
             ORDER BY identificacao
            """,
            rs -> {
                SalaId salaId = new SalaId(rs.getObject("id", UUID.class));
                CargoId cargoId = new CargoId(rs.getObject("cargo_atendido_id", UUID.class));
                local.cadastrarSala(
                    salaId,
                    rs.getString("identificacao"),
                    rs.getInt("capacidade_maxima"),
                    cargoId,
                    new Fiscal(rs.getString("nome_fiscal"), new CPF(rs.getString("cpf_fiscal")))
                );

                List<UUID> alocadas = jdbcTemplate.query(
                    "SELECT inscricao_id FROM logistica.alocacoes_de_sala WHERE sala_id = ?",
                    (r, idx) -> r.getObject("inscricao_id", UUID.class),
                    salaId.valor()
                );
                for (UUID inscId : alocadas) {
                    local.alocarCandidatoEmSalaEspecifica(salaId, new InscricaoId(inscId), cargoId);
                }
            },
            local.id().valor()
        );
    }

    @Override
    public void salvar(AlocacaoDeSala alocacao) {
        ListaDePresenca presenca = alocacao.listaDePresenca();
        jdbcTemplate.update(
            """
            INSERT INTO logistica.alocacoes_de_sala (
                id, inscricao_id, cargo_id, local_de_prova_id, sala_id,
                presente, hash_assinatura, documento_apresentado, registrado_em, alocado_em
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (inscricao_id) DO UPDATE SET
                presente = EXCLUDED.presente,
                hash_assinatura = EXCLUDED.hash_assinatura,
                documento_apresentado = EXCLUDED.documento_apresentado,
                registrado_em = EXCLUDED.registrado_em
            """,
            alocacao.id().valor(),
            alocacao.inscricaoId().valor(),
            alocacao.cargoId().valor(),
            alocacao.localDeProvaId().valor(),
            alocacao.salaId().valor(),
            presenca != null ? presenca.presente() : null,
            presenca != null ? presenca.hashAssinaturaCandidato() : null,
            presenca != null ? presenca.documentoApresentado() : null,
            presenca != null ? Timestamp.from(presenca.registradoEm()) : null,
            Timestamp.from(Instant.now())
        );
    }

    @Override
    public void salvarTodas(List<AlocacaoDeSala> alocacoes) {
        for (AlocacaoDeSala alocacao : alocacoes) {
            salvar(alocacao);
        }
    }

    @Override
    public Optional<AlocacaoDeSala> buscarPorId(AlocacaoDeSalaId id) {
        return buscarAlocacaoPorWhere("id = ?", id.valor());
    }

    @Override
    public Optional<AlocacaoDeSala> buscarPorInscricao(InscricaoId inscricaoId) {
        return buscarAlocacaoPorWhere("inscricao_id = ?", inscricaoId.valor());
    }

    private Optional<AlocacaoDeSala> buscarAlocacaoPorWhere(String whereClause, UUID param) {
        List<AlocacaoDeSala> resultado = jdbcTemplate.query(
            """
            SELECT id, inscricao_id, cargo_id, local_de_prova_id, sala_id,
                   presente, hash_assinatura, documento_apresentado, registrado_em
              FROM logistica.alocacoes_de_sala
             WHERE %s
            """.formatted(whereClause),
            (rs, rowNum) -> {
                Boolean presente = rs.getObject("presente", Boolean.class);
                ListaDePresenca presenca = null;
                if (presente != null) {
                    Timestamp ts = rs.getTimestamp("registrado_em");
                    Instant regEm = ts != null ? ts.toInstant() : Instant.now();
                    presenca = presente
                        ? ListaDePresenca.assinada(
                            rs.getString("hash_assinatura"),
                            rs.getString("documento_apresentado"),
                            regEm
                        )
                        : ListaDePresenca.ausente(regEm);
                }
                return AlocacaoDeSala.reconstituir(
                    new AlocacaoDeSalaId(rs.getObject("id", UUID.class)),
                    new InscricaoId(rs.getObject("inscricao_id", UUID.class)),
                    new CargoId(rs.getObject("cargo_id", UUID.class)),
                    new LocalDeProvaId(rs.getObject("local_de_prova_id", UUID.class)),
                    new SalaId(rs.getObject("sala_id", UUID.class)),
                    presenca
                );
            },
            param
        );
        return resultado.stream().findFirst();
    }

    @Override
    public List<CandidatoHomologadoParaAlocacao> listarHomologadosPendentesDeAlocacao(ConcursoId concursoId) {
        return jdbcTemplate.query(
            """
            SELECT i.id AS inscricao_id, i.cargo_id,
                   c.logradouro, c.numero, c.bairro, c.cidade, c.uf, c.cep, c.latitude, c.longitude
              FROM inscricao.inscricoes i
              JOIN identidade.usuarios c ON c.id = i.candidato_id
              LEFT JOIN logistica.alocacoes_de_sala a ON a.inscricao_id = i.id
             WHERE i.concurso_id = ?
               AND i.status IN ('HOMOLOGADA', 'PAGA', 'ISENTA')
               AND a.id IS NULL
             ORDER BY i.numero_inscricao ASC
            """,
            (rs, rowNum) -> {
                Endereco endereco = new Endereco(
                    rs.getString("logradouro"),
                    rs.getString("numero"),
                    rs.getString("bairro"),
                    rs.getString("cidade"),
                    rs.getString("uf"),
                    rs.getString("cep"),
                    rs.getObject("latitude", Double.class),
                    rs.getObject("longitude", Double.class)
                );
                return new CandidatoHomologadoParaAlocacao(
                    new InscricaoId(rs.getObject("inscricao_id", UUID.class)),
                    new CargoId(rs.getObject("cargo_id", UUID.class)),
                    endereco
                );
            },
            concursoId.valor()
        );
    }
}
