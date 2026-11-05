package br.com.keyfalls.logistica.infrastructure.persistence;

import br.com.keyfalls.shared.domain.exception.CapacidadeSalaExcedidaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(schema = "logistica", name = "salas")
public class SalaJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    @Column(name = "local_de_prova_id", nullable = false)
    private UUID localDeProvaId;

    @Column(name = "identificacao", nullable = false, length = 60)
    private String identificacao;

    @Column(name = "capacidade_maxima", nullable = false)
    private int capacidadeMaxima;

    @Column(name = "vagas_ocupadas", nullable = false)
    private int vagasOcupadas;

    @Column(name = "cargo_atendido_id", nullable = false)
    private UUID cargoAtendidoId;

    protected SalaJpaEntity() {}

    public SalaJpaEntity(UUID id, UUID localDeProvaId, String identificacao, int capacidadeMaxima, int vagasOcupadas, UUID cargoAtendidoId) {
        this.id = id;
        this.localDeProvaId = localDeProvaId;
        this.identificacao = identificacao;
        this.capacidadeMaxima = capacidadeMaxima;
        this.vagasOcupadas = vagasOcupadas;
        this.cargoAtendidoId = cargoAtendidoId;
    }

    public void ocuparUmaVaga() {
        if (vagasOcupadas >= capacidadeMaxima) {
            throw new CapacidadeSalaExcedidaException(
                "Capacidade maxima (%d) atingida na sala '%s'.".formatted(capacidadeMaxima, identificacao)
            );
        }
        this.vagasOcupadas++;
    }

    public UUID getId() { return id; }
    public Long getVersao() { return versao; }
    public int getCapacidadeMaxima() { return capacidadeMaxima; }
    public int getVagasOcupadas() { return vagasOcupadas; }
}
