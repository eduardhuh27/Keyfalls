package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.logistica.domain.event.SalaAlocada;
import br.com.keyfalls.shared.domain.AggregateRoot;
import br.com.keyfalls.shared.domain.AlocacaoDeSalaId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.SalaId;

import java.time.Instant;
import java.util.Objects;

public final class AlocacaoDeSala extends AggregateRoot<AlocacaoDeSalaId> {

    private final AlocacaoDeSalaId id;
    private final InscricaoId inscricaoId;
    private final CargoId cargoId;
    private final LocalDeProvaId localDeProvaId;
    private final SalaId salaId;
    private ListaDePresenca listaDePresenca;

    private AlocacaoDeSala(
        AlocacaoDeSalaId id,
        InscricaoId inscricaoId,
        CargoId cargoId,
        LocalDeProvaId localDeProvaId,
        SalaId salaId
    ) {
        this.id = Objects.requireNonNull(id, "AlocacaoDeSalaId obrigatorio.");
        this.inscricaoId = Objects.requireNonNull(inscricaoId, "InscricaoId obrigatorio.");
        this.cargoId = Objects.requireNonNull(cargoId, "CargoId obrigatorio.");
        this.localDeProvaId = Objects.requireNonNull(localDeProvaId, "LocalDeProvaId obrigatorio.");
        this.salaId = Objects.requireNonNull(salaId, "SalaId obrigatorio.");
    }

    public static AlocacaoDeSala vincular(
        AlocacaoDeSalaId id,
        InscricaoId inscricaoId,
        CargoId cargoId,
        LocalDeProvaId localDeProvaId,
        SalaId salaId,
        Instant alocadoEm
    ) {
        AlocacaoDeSala alocacao = new AlocacaoDeSala(id, inscricaoId, cargoId, localDeProvaId, salaId);
        alocacao.registerEvent(SalaAlocada.novo(id, inscricaoId, cargoId, localDeProvaId, salaId, alocadoEm));
        return alocacao;
    }

    public static AlocacaoDeSala vincular(
        AlocacaoDeSalaId id,
        InscricaoId inscricaoId,
        CargoId cargoId,
        LocalDeProvaId localDeProvaId,
        SalaId salaId
    ) {
        return vincular(id, inscricaoId, cargoId, localDeProvaId, salaId, Instant.now());
    }

    public static AlocacaoDeSala reconstituir(
        AlocacaoDeSalaId id,
        InscricaoId inscricaoId,
        CargoId cargoId,
        LocalDeProvaId localDeProvaId,
        SalaId salaId,
        ListaDePresenca listaDePresenca
    ) {
        AlocacaoDeSala alocacao = new AlocacaoDeSala(id, inscricaoId, cargoId, localDeProvaId, salaId);
        alocacao.listaDePresenca = listaDePresenca;
        return alocacao;
    }

    public void registrarPresencaAssinada(String hashAssinatura, String documentoApresentado, Instant horario) {
        exigirPresencaNaoRegistrada();
        this.listaDePresenca = ListaDePresenca.assinada(hashAssinatura, documentoApresentado, horario);
    }

    public void registrarAusencia(Instant horarioFechamentoPortoes) {
        exigirPresencaNaoRegistrada();
        this.listaDePresenca = ListaDePresenca.ausente(horarioFechamentoPortoes);
    }

    private void exigirPresencaNaoRegistrada() {
        if (this.listaDePresenca != null) {
            throw new RegraDeNegocioException("A lista de presenca ja foi registrada para esta alocacao.");
        }
    }

    @Override
    public AlocacaoDeSalaId id() {
        return id;
    }

    public InscricaoId inscricaoId() {
        return inscricaoId;
    }

    public CargoId cargoId() {
        return cargoId;
    }

    public LocalDeProvaId localDeProvaId() {
        return localDeProvaId;
    }

    public SalaId salaId() {
        return salaId;
    }

    public ListaDePresenca listaDePresenca() {
        return listaDePresenca;
    }
}
