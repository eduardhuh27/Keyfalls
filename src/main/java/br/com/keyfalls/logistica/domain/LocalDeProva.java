package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.AggregateRoot;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.SalaId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class LocalDeProva extends AggregateRoot<LocalDeProvaId> {

    private final LocalDeProvaId id;
    private final ConcursoId concursoId;
    private final String nomeInstituicao;
    private final Endereco endereco;
    private final List<Sala> salas;

    private LocalDeProva(
        LocalDeProvaId id,
        ConcursoId concursoId,
        String nomeInstituicao,
        Endereco endereco
    ) {
        this.id = Objects.requireNonNull(id, "LocalDeProvaId obrigatorio.");
        this.concursoId = Objects.requireNonNull(concursoId, "ConcursoId obrigatorio.");
        if (nomeInstituicao == null || nomeInstituicao.isBlank()) {
            throw new RegraDeNegocioException("Nome da instituicao do local de prova obrigatorio.");
        }
        this.nomeInstituicao = nomeInstituicao.trim();
        this.endereco = Objects.requireNonNull(endereco, "Endereco do local de prova obrigatorio.");
        this.salas = new ArrayList<>();
    }

    public static LocalDeProva credenciar(
        LocalDeProvaId id,
        ConcursoId concursoId,
        String nomeInstituicao,
        Endereco endereco
    ) {
        return new LocalDeProva(id, concursoId, nomeInstituicao, endereco);
    }

    public Sala cadastrarSala(
        SalaId salaId,
        String identificacao,
        int capacidadeMaxima,
        CargoId cargoAtendido,
        Fiscal fiscal
    ) {
        boolean duplicada = salas.stream()
            .anyMatch(s -> s.id().equals(salaId) || s.identificacao().equalsIgnoreCase(identificacao));
        if (duplicada) {
            throw new RegraDeNegocioException("Sala ja cadastrada neste local de prova: " + identificacao);
        }
        Sala sala = new Sala(salaId, identificacao, capacidadeMaxima, cargoAtendido, fiscal);
        this.salas.add(sala);
        return sala;
    }

    public Sala alocarCandidatoEmSalaEspecifica(SalaId salaId, InscricaoId inscricaoId, CargoId cargoCandidato) {
        Sala sala = buscarSala(salaId);
        sala.reservarVagaParaCandidato(inscricaoId, cargoCandidato);
        return sala;
    }

    public Optional<Sala> encontrarPrimeiraSalaDisponivelParaCargo(CargoId cargoId) {
        return salas.stream()
            .filter(s -> s.possuiVagaParaCargo(cargoId))
            .findFirst();
    }

    public void substituirFiscalDaSala(SalaId salaId, Fiscal novoFiscal) {
        buscarSala(salaId).substituirFiscal(novoFiscal);
    }

    private Sala buscarSala(SalaId salaId) {
        return salas.stream()
            .filter(s -> s.id().equals(salaId))
            .findFirst()
            .orElseThrow(() -> new RegraDeNegocioException("Sala nao encontrada neste local de prova."));
    }

    @Override
    public LocalDeProvaId id() {
        return id;
    }

    public ConcursoId concursoId() {
        return concursoId;
    }

    public String nomeInstituicao() {
        return nomeInstituicao;
    }

    public Endereco endereco() {
        return endereco;
    }

    public List<Sala> salas() {
        return Collections.unmodifiableList(salas);
    }
}
