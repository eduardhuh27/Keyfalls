package br.com.keyfalls.recurso.infrastructure.persistence;

import br.com.keyfalls.shared.domain.exception.ConflitoDeEstadoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "recurso", name = "recursos")
public class RecursoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    @Column(name = "concurso_id", nullable = false)
    private UUID concursoId;

    @Column(name = "prova_id", nullable = false)
    private UUID provaId;

    @Column(name = "questao_id", nullable = false)
    private UUID questaoId;

    @Column(name = "candidato_id", nullable = false)
    private UUID candidatoId;

    @Column(name = "fundamentacao", nullable = false, columnDefinition = "TEXT")
    private String fundamentacao;

    @Column(name = "hash_agrupamento", nullable = false, length = 64)
    private String hashAgrupamento;

    @Column(name = "decisao", length = 20)
    private String decisao;

    @Column(name = "parecer_banca", columnDefinition = "TEXT")
    private String parecerBanca;

    @Column(name = "protocolado_em", nullable = false)
    private Instant protocoladoEm;

    @Column(name = "julgado_em")
    private Instant julgadoEm;

    protected RecursoJpaEntity() {}

    public RecursoJpaEntity(
        UUID id,
        UUID concursoId,
        UUID provaId,
        UUID questaoId,
        UUID candidatoId,
        String fundamentacao,
        String hashAgrupamento,
        Instant protocoladoEm
    ) {
        this.id = id;
        this.concursoId = concursoId;
        this.provaId = provaId;
        this.questaoId = questaoId;
        this.candidatoId = candidatoId;
        this.fundamentacao = fundamentacao;
        this.hashAgrupamento = hashAgrupamento;
        this.protocoladoEm = protocoladoEm;
    }

    public void registrarJulgamento(String decisao, String parecerBanca, Instant julgadoEm) {
        if (this.decisao != null) {
            throw new ConflitoDeEstadoException("Recurso ja foi julgado anteriormente (" + this.decisao + ").");
        }
        this.decisao = decisao;
        this.parecerBanca = parecerBanca;
        this.julgadoEm = julgadoEm;
    }

    public UUID getId() { return id; }
    public Long getVersao() { return versao; }
    public UUID getConcursoId() { return concursoId; }
    public String getDecisao() { return decisao; }
}
