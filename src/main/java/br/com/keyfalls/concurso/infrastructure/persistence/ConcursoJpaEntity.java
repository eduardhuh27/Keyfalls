package br.com.keyfalls.concurso.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Entidade JPA de Concurso equipada com @Version (Optimistic Locking)
 * para impedir condicoes de corrida entre julgamentos tardios e a homologacao final.
 */
@Entity
@Table(schema = "concurso", name = "concursos")
public class ConcursoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    @Column(name = "codigo_edital", nullable = false, unique = true, length = 50)
    private String codigoEdital;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "abrangencia", nullable = false, length = 20)
    private String abrangencia;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "periodo_inscricao_inicio", nullable = false)
    private Instant periodoInscricaoInicio;

    @Column(name = "periodo_inscricao_fim", nullable = false)
    private Instant periodoInscricaoFim;

    @Column(name = "periodo_isencao_inicio", nullable = false)
    private Instant periodoIsencaoInicio;

    @Column(name = "periodo_isencao_fim", nullable = false)
    private Instant periodoIsencaoFim;

    @Column(name = "politica_anulacao", nullable = false, length = 40)
    private String politicaAnulacao;

    protected ConcursoJpaEntity() {}

    public UUID getId() { return id; }
    public Long getVersao() { return versao; }
    public String getStatus() { return status; }
}
