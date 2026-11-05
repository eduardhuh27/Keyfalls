package br.com.keyfalls.concurso.domain;

import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Prova {

    private final ProvaId id;
    private final CargoId cargoId;
    private final String disciplina;
    private final Peso peso;
    private final List<Questao> questoes;
    private Gabarito gabarito;

    Prova(ProvaId id, CargoId cargoId, String disciplina, Peso peso) {
        this.id = Objects.requireNonNull(id, "ProvaId obrigatorio.");
        this.cargoId = Objects.requireNonNull(cargoId, "CargoId obrigatorio.");
        if (disciplina == null || disciplina.isBlank()) {
            throw new RegraDeNegocioException("Disciplina/titulo da prova obrigatorio.");
        }
        this.disciplina = disciplina.trim();
        this.peso = Objects.requireNonNull(peso, "Peso da prova obrigatorio.");
        this.questoes = new ArrayList<>();
    }

    Questao adicionarQuestao(QuestaoId questaoId, int numeroOrdem, String enunciado, Map<Alternativa, String> opcoes) {
        if (gabarito != null) {
            throw new RegraDeNegocioException("Nao e permitido incluir questoes apos a publicacao do gabarito.");
        }
        boolean numeroDuplicado = questoes.stream().anyMatch(q -> q.numeroOrdem() == numeroOrdem);
        if (numeroDuplicado) {
            throw new RegraDeNegocioException("Ja existe questao com numero de ordem " + numeroOrdem + " nesta prova.");
        }
        Questao nova = new Questao(questaoId, numeroOrdem, enunciado, opcoes);
        this.questoes.add(nova);
        return nova;
    }

    Gabarito publicarGabaritoPreliminar(Map<QuestaoId, Alternativa> respostas) {
        if (questoes.isEmpty()) {
            throw new RegraDeNegocioException("A prova deve possuir questoes antes da publicacao do gabarito.");
        }
        if (respostas.size() != questoes.size()) {
            throw new RegraDeNegocioException("O gabarito deve contemplar exatamente todas as questoes da prova.");
        }
        for (Questao questao : questoes) {
            Alternativa resposta = respostas.get(questao.id());
            if (resposta == null || !questao.possuiAlternativa(resposta)) {
                throw new RegraDeNegocioException("Alternativa de gabarito invalida ou ausente para a questao " + questao.numeroOrdem());
            }
        }
        this.gabarito = new Gabarito(respostas);
        return this.gabarito;
    }

    void anularQuestao(QuestaoId questaoId, String justificativa) {
        if (gabarito != null && gabarito.isDefinitivo()) {
            throw new RegraDeNegocioException("Nao e permitido anular questao apos consolidacao do gabarito definitivo.");
        }
        buscarQuestao(questaoId).anular(justificativa);
    }

    void retificarGabaritoQuestao(QuestaoId questaoId, Alternativa novaAlternativa) {
        if (gabarito == null) {
            throw new RegraDeNegocioException("Gabarito ainda nao publicado para esta prova.");
        }
        Questao questao = buscarQuestao(questaoId);
        if (questao.isAnulada()) {
            throw new RegraDeNegocioException("Nao e permitido alterar alternativa de uma questao anulada.");
        }
        if (!questao.possuiAlternativa(novaAlternativa)) {
            throw new RegraDeNegocioException("A questao nao possui a alternativa informada.");
        }
        gabarito.retificarResposta(questaoId, novaAlternativa);
    }

    void consolidarGabaritoDefinitivo() {
        if (gabarito == null) {
            throw new RegraDeNegocioException("Nao ha gabarito preliminar publicado para consolidar.");
        }
        gabarito.consolidarDefinitivo();
    }

    private Questao buscarQuestao(QuestaoId questaoId) {
        return questoes.stream()
            .filter(q -> q.id().equals(questaoId))
            .findFirst()
            .orElseThrow(() -> new RegraDeNegocioException("Questao nao encontrada nesta prova."));
    }

    public ProvaId id() {
        return id;
    }

    public CargoId cargoId() {
        return cargoId;
    }

    public String disciplina() {
        return disciplina;
    }

    public Peso peso() {
        return peso;
    }

    public List<Questao> questoes() {
        return Collections.unmodifiableList(questoes);
    }

    public Gabarito gabarito() {
        return gabarito;
    }
}
