package br.com.keyfalls.inscricao.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "inscricao", name = "inscricoes")
public class InscricaoJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    @Column(name = "numero_inscricao", nullable = false, unique = true, length = 20)
    private String numeroInscricao;

    @Column(name = "candidato_id", nullable = false, updatable = false)
    private UUID candidatoId;

    @Column(name = "concurso_id", nullable = false, updatable = false)
    private UUID concursoId;

    @Column(name = "cargo_id", nullable = false, updatable = false)
    private UUID cargoId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "boleto_linha_digitavel", length = 64)
    private String boletoLinhaDigitavel;

    @Column(name = "boleto_valor", precision = 10, scale = 2)
    private BigDecimal boletoValor;

    @Column(name = "boleto_vencimento")
    private LocalDate boletoVencimento;

    @Column(name = "boleto_pago")
    private boolean boletoPago;

    @Column(name = "boleto_liquidado_em")
    private Instant boletoLiquidadoEm;

    @Column(name = "isencao_justificativa", columnDefinition = "TEXT")
    private String isencaoJustificativa;

    @Column(name = "isencao_solicitada_em")
    private Instant isencaoSolicitadaEm;

    @Column(name = "isencao_situacao", length = 20)
    private String isencaoSituacao;

    @Column(name = "isencao_parecer", columnDefinition = "TEXT")
    private String isencaoParecer;

    @Column(name = "isencao_avaliada_em")
    private Instant isencaoAvaliadaEm;

    protected InscricaoJpaEntity() {
        // Construtor exigido exclusivamente pelo JPA na camada de infraestrutura
    }

    public InscricaoJpaEntity(
        UUID id,
        String numeroInscricao,
        UUID candidatoId,
        UUID concursoId,
        UUID cargoId,
        String status,
        String boletoLinhaDigitavel,
        BigDecimal boletoValor,
        LocalDate boletoVencimento,
        boolean boletoPago,
        Instant boletoLiquidadoEm,
        String isencaoJustificativa,
        Instant isencaoSolicitadaEm,
        String isencaoSituacao,
        String isencaoParecer,
        Instant isencaoAvaliadaEm
    ) {
        this.id = id;
        this.numeroInscricao = numeroInscricao;
        this.candidatoId = candidatoId;
        this.concursoId = concursoId;
        this.cargoId = cargoId;
        this.status = status;
        this.boletoLinhaDigitavel = boletoLinhaDigitavel;
        this.boletoValor = boletoValor;
        this.boletoVencimento = boletoVencimento;
        this.boletoPago = boletoPago;
        this.boletoLiquidadoEm = boletoLiquidadoEm;
        this.isencaoJustificativa = isencaoJustificativa;
        this.isencaoSolicitadaEm = isencaoSolicitadaEm;
        this.isencaoSituacao = isencaoSituacao;
        this.isencaoParecer = isencaoParecer;
        this.isencaoAvaliadaEm = isencaoAvaliadaEm;
    }

    public UUID getId() { return id; }
    public String getNumeroInscricao() { return numeroInscricao; }
    public UUID getCandidatoId() { return candidatoId; }
    public UUID getConcursoId() { return concursoId; }
    public UUID getCargoId() { return cargoId; }
    public String getStatus() { return status; }
    public String getBoletoLinhaDigitavel() { return boletoLinhaDigitavel; }
    public BigDecimal getBoletoValor() { return boletoValor; }
    public LocalDate getBoletoVencimento() { return boletoVencimento; }
    public boolean isBoletoPago() { return boletoPago; }
    public Instant getBoletoLiquidadoEm() { return boletoLiquidadoEm; }
    public String getIsencaoJustificativa() { return isencaoJustificativa; }
    public Instant getIsencaoSolicitadaEm() { return isencaoSolicitadaEm; }
    public String getIsencaoSituacao() { return isencaoSituacao; }
    public String getIsencaoParecer() { return isencaoParecer; }
    public Instant getIsencaoAvaliadaEm() { return isencaoAvaliadaEm; }
}
