package br.com.keyfalls.recurso.domain;

import br.com.keyfalls.concurso.domain.event.QuestaoAnulada;
import br.com.keyfalls.shared.domain.AggregateRoot;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RecursoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.time.Instant;
import java.util.Objects;

public final class Recurso extends AggregateRoot<RecursoId> {

    private final RecursoId id;
    private final ConcursoId concursoId;
    private final ProvaId provaId;
    private final QuestaoId questaoId;
    private final CandidatoId candidatoId;
    private final String fundamentacao;
    private final String hashAgrupamentoArgumentacao;
    private final Instant protocoladoEm;
    private DecisaoRecurso decisao;
    private String parecerBanca;
    private Instant julgadoEm;

    private Recurso(
        RecursoId id,
        ConcursoId concursoId,
        ProvaId provaId,
        QuestaoId questaoId,
        CandidatoId candidatoId,
        String fundamentacao,
        Instant protocoladoEm
    ) {
        this.id = Objects.requireNonNull(id, "RecursoId obrigatorio.");
        this.concursoId = Objects.requireNonNull(concursoId, "ConcursoId obrigatorio.");
        this.provaId = Objects.requireNonNull(provaId, "ProvaId obrigatorio.");
        this.questaoId = Objects.requireNonNull(questaoId, "QuestaoId obrigatorio.");
        this.candidatoId = Objects.requireNonNull(candidatoId, "CandidatoId obrigatorio.");
        if (fundamentacao == null || fundamentacao.trim().length() < 15) {
            throw new RegraDeNegocioException("A fundamentacao do recurso deve conter argumentacao minima de 15 caracteres.");
        }
        this.fundamentacao = fundamentacao.trim();
        this.hashAgrupamentoArgumentacao = ValidadorAberturaRecurso.gerarHashAgrupamentoArgumentacao(this.fundamentacao);
        this.protocoladoEm = Objects.requireNonNull(protocoladoEm, "Data/hora de protocolo do recurso obrigatoria.");
    }

    public static Recurso interpor(
        RecursoId id,
        ConcursoId concursoId,
        ProvaId provaId,
        QuestaoId questaoId,
        CandidatoId candidatoId,
        String fundamentacao,
        Instant protocoladoEm
    ) {
        return new Recurso(id, concursoId, provaId, questaoId, candidatoId, fundamentacao, protocoladoEm);
    }

    public static Recurso reconstituir(
        RecursoId id,
        ConcursoId concursoId,
        ProvaId provaId,
        QuestaoId questaoId,
        CandidatoId candidatoId,
        String fundamentacao,
        Instant protocoladoEm,
        DecisaoRecurso decisao,
        String parecerBanca,
        Instant julgadoEm
    ) {
        Recurso recurso = new Recurso(id, concursoId, provaId, questaoId, candidatoId, fundamentacao, protocoladoEm);
        recurso.decisao = decisao;
        recurso.parecerBanca = parecerBanca;
        recurso.julgadoEm = julgadoEm;
        return recurso;
    }

    public static Recurso interporValidandoEdital(
        RecursoId id,
        ConcursoId concursoId,
        ProvaId provaId,
        QuestaoId questaoId,
        CandidatoId candidatoId,
        ValidadorAberturaRecurso.ContextoAberturaRecurso contextoValidacao
    ) {
        Objects.requireNonNull(contextoValidacao, "Contexto de validacao de abertura do recurso obrigatorio.");
        ValidadorAberturaRecurso.validarOuRejeitar(contextoValidacao);
        return new Recurso(
            id, concursoId, provaId, questaoId, candidatoId,
            contextoValidacao.fundamentacao(), contextoValidacao.protocoladoEm()
        );
    }

    public void deferir(String parecerBanca, Instant julgadoEm) {
        validarJulgamentoPendente(parecerBanca, julgadoEm);
        this.decisao = DecisaoRecurso.DEFERIDO;
        this.parecerBanca = parecerBanca.trim();
        this.julgadoEm = julgadoEm;
    }

    /**
     * Deferimento que determina a anulacao da questao contestada, registrando
     * o evento QuestaoAnulada para coreografia assincrona com Concurso e Apuracao.
     */
    public void deferirComAnulacaoDeQuestao(String parecerBanca, Instant julgadoEm) {
        deferir(parecerBanca, julgadoEm);
        registerEvent(QuestaoAnulada.novo(
            this.concursoId, this.provaId, this.questaoId, this.parecerBanca, julgadoEm
        ));
    }

    public void indeferir(String parecerBanca, Instant julgadoEm) {
        validarJulgamentoPendente(parecerBanca, julgadoEm);
        this.decisao = DecisaoRecurso.INDEFERIDO;
        this.parecerBanca = parecerBanca.trim();
        this.julgadoEm = julgadoEm;
    }

    public boolean foiJulgado() {
        return this.decisao != null;
    }

    public boolean foiDeferido() {
        return this.decisao == DecisaoRecurso.DEFERIDO;
    }

    private void validarJulgamentoPendente(String parecer, Instant instanteJulgamento) {
        if (this.decisao != null) {
            throw new RegraDeNegocioException("Este recurso ja foi julgado anteriormente (" + this.decisao + ").");
        }
        if (parecer == null || parecer.isBlank()) {
            throw new RegraDeNegocioException("Parecer oficial da banca examinadora obrigatorio.");
        }
        Objects.requireNonNull(instanteJulgamento, "Data/hora de julgamento obrigatoria.");
    }

    @Override
    public RecursoId id() {
        return id;
    }

    public ConcursoId concursoId() {
        return concursoId;
    }

    public ProvaId provaId() {
        return provaId;
    }

    public QuestaoId questaoId() {
        return questaoId;
    }

    public CandidatoId candidatoId() {
        return candidatoId;
    }

    public String fundamentacao() {
        return fundamentacao;
    }

    public String hashAgrupamentoArgumentacao() {
        return hashAgrupamentoArgumentacao;
    }

    public Instant protocoladoEm() {
        return protocoladoEm;
    }

    public DecisaoRecurso decisao() {
        return decisao;
    }

    public String parecerBanca() {
        return parecerBanca;
    }

    public Instant julgadoEm() {
        return julgadoEm;
    }
}
