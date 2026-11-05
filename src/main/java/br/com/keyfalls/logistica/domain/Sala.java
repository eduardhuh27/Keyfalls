package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.SalaId;
import br.com.keyfalls.shared.domain.exception.CapacidadeSalaExcedidaException;
import br.com.keyfalls.shared.domain.exception.CargoIncompativelNaSalaException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Sala {

    private final SalaId id;
    private final String identificacao;
    private final int capacidadeMaxima;
    private final CargoId cargoAtendido;
    private Fiscal fiscal;
    private final List<InscricaoId> inscricoesAlocadas;

    Sala(SalaId id, String identificacao, int capacidadeMaxima, CargoId cargoAtendido, Fiscal fiscal) {
        this.id = Objects.requireNonNull(id, "SalaId obrigatorio.");
        if (identificacao == null || identificacao.isBlank()) {
            throw new RegraDeNegocioException("Identificacao da sala obrigatoria.");
        }
        if (capacidadeMaxima <= 0) {
            throw new RegraDeNegocioException("Capacidade maxima da sala deve ser maior que zero.");
        }
        this.cargoAtendido = Objects.requireNonNull(cargoAtendido, "Cargo atendido pela sala obrigatorio.");
        this.fiscal = Objects.requireNonNull(fiscal, "Fiscal responsavel pela sala obrigatorio.");
        this.identificacao = identificacao.trim();
        this.capacidadeMaxima = capacidadeMaxima;
        this.inscricoesAlocadas = new ArrayList<>();
    }

    /**
     * Regra de Negocio (Homogeneidade de Sala): Uma Sala so pode receber alocacao
     * de candidatos inscritos para o mesmo Cargo configurado para aquela sala,
     * respeitando o limite de capacidade maxima.
     */
    void reservarVagaParaCandidato(InscricaoId inscricaoId, CargoId cargoCandidato) {
        Objects.requireNonNull(inscricaoId, "InscricaoId obrigatorio.");
        Objects.requireNonNull(cargoCandidato, "CargoId do candidato obrigatorio.");

        if (!this.cargoAtendido.equals(cargoCandidato)) {
            throw new CargoIncompativelNaSalaException(
                "Violacao de homogeneidade de sala: a sala '%s' atende exclusivamente o cargo %s."
                    .formatted(identificacao, cargoAtendido.valor())
            );
        }
        if (inscricoesAlocadas.contains(inscricaoId)) {
            throw new RegraDeNegocioException("A inscricao informada ja esta alocada nesta sala.");
        }
        if (inscricoesAlocadas.size() >= capacidadeMaxima) {
            throw new CapacidadeSalaExcedidaException(
                "Capacidade maxima (%d) atingida na sala '%s'.".formatted(capacidadeMaxima, identificacao)
            );
        }
        inscricoesAlocadas.add(inscricaoId);
    }

    void substituirFiscal(Fiscal novoFiscal) {
        this.fiscal = Objects.requireNonNull(novoFiscal, "Novo fiscal obrigatorio.");
    }

    public boolean possuiVagaParaCargo(CargoId cargoCandidato) {
        return this.cargoAtendido.equals(cargoCandidato) && inscricoesAlocadas.size() < capacidadeMaxima;
    }

    public int vagasDisponiveis() {
        return capacidadeMaxima - inscricoesAlocadas.size();
    }

    public SalaId id() {
        return id;
    }

    public String identificacao() {
        return identificacao;
    }

    public int capacidadeMaxima() {
        return capacidadeMaxima;
    }

    public CargoId cargoAtendido() {
        return cargoAtendido;
    }

    public Fiscal fiscal() {
        return fiscal;
    }

    public List<InscricaoId> inscricoesAlocadas() {
        return Collections.unmodifiableList(inscricoesAlocadas);
    }
}
