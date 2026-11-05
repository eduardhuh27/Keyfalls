package br.com.keyfalls.inscricao.domain;

import br.com.keyfalls.inscricao.domain.event.InscricaoRealizada;
import br.com.keyfalls.inscricao.domain.event.IsencaoJulgada;
import br.com.keyfalls.shared.domain.AggregateRoot;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.Periodo;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.exception.PrazoIsencaoEncerradoException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class Inscricao extends AggregateRoot<InscricaoId> {

    private final InscricaoId id;
    private final NumeroInscricao numero;
    private final CandidatoId candidatoId;
    private final ConcursoId concursoId;
    private final CargoId cargoId;
    private StatusInscricao status;
    private Boleto boleto;
    private PedidoIsencao pedidoIsencao;

    private Inscricao(
        InscricaoId id,
        NumeroInscricao numero,
        CandidatoId candidatoId,
        ConcursoId concursoId,
        CargoId cargoId
    ) {
        this.id = Objects.requireNonNull(id, "InscricaoId obrigatorio.");
        this.numero = Objects.requireNonNull(numero, "NumeroInscricao obrigatorio.");
        this.candidatoId = Objects.requireNonNull(candidatoId, "CandidatoId obrigatorio.");
        this.concursoId = Objects.requireNonNull(concursoId, "ConcursoId obrigatorio.");
        this.cargoId = Objects.requireNonNull(cargoId, "CargoId obrigatorio.");
        this.status = StatusInscricao.AGUARDANDO_PAGAMENTO;
    }

    public static Inscricao realizar(
        InscricaoId id,
        NumeroInscricao numero,
        CandidatoId candidatoId,
        ConcursoId concursoId,
        CargoId cargoId,
        Periodo periodoInscricaoConcurso,
        Instant instanteAtual
    ) {
        Objects.requireNonNull(periodoInscricaoConcurso, "Periodo de inscricao do concurso obrigatorio.");
        if (!periodoInscricaoConcurso.contemEstritamente(instanteAtual)) {
            throw new RegraDeNegocioException("Fora do prazo regulamentar de inscricao do concurso.");
        }
        Inscricao inscricao = new Inscricao(id, numero, candidatoId, concursoId, cargoId);
        inscricao.registerEvent(
            InscricaoRealizada.novo(id, numero, candidatoId, concursoId, cargoId, instanteAtual)
        );
        return inscricao;
    }

    /**
     * Factory de reconstituicao exclusiva para mappers de persistencia (hidratacao a partir do banco),
     * restaurando o estado sem disparar novos eventos de dominio nem expor setters publicos.
     */
    public static Inscricao reconstituir(
        InscricaoId id,
        NumeroInscricao numero,
        CandidatoId candidatoId,
        ConcursoId concursoId,
        CargoId cargoId,
        StatusInscricao status,
        Boleto boleto,
        PedidoIsencao pedidoIsencao
    ) {
        Inscricao inscricao = new Inscricao(id, numero, candidatoId, concursoId, cargoId);
        inscricao.status = Objects.requireNonNull(status, "Status obrigatorio na reconstituicao.");
        inscricao.boleto = boleto;
        inscricao.pedidoIsencao = pedidoIsencao;
        return inscricao;
    }

    public Boleto gerarBoleto(String linhaDigitavel, BigDecimal valorTaxa, LocalDate vencimento) {
        if (status == StatusInscricao.HOMOLOGADA || status == StatusInscricao.CANCELADA) {
            throw new RegraDeNegocioException("Nao e possivel emitir boleto para inscricao com status " + status);
        }
        if (status == StatusInscricao.ISENCAO_EM_ANALISE) {
            throw new RegraDeNegocioException("Ha um pedido de isencao pendente de julgamento para esta inscricao.");
        }
        this.boleto = Boleto.emitir(linhaDigitavel, valorTaxa, vencimento);
        return this.boleto;
    }

    public void homologarPorPagamento(Instant dataPagamento) {
        if (this.boleto == null) {
            throw new RegraDeNegocioException("Nenhum boleto emitido para liquidacao nesta inscricao.");
        }
        if (this.status == StatusInscricao.CANCELADA) {
            throw new RegraDeNegocioException("Inscricao cancelada nao pode ser homologada.");
        }
        this.boleto = this.boleto.liquidar(dataPagamento);
        this.status = StatusInscricao.HOMOLOGADA;
    }

    /**
     * Regra de Negocio: O pedido de isencao so pode ser solicitado se a data atual
     * estiver estritamente dentro do Periodo de isencao do concurso.
     */
    public PedidoIsencao solicitarIsencao(
        String justificativaLegal,
        Periodo periodoIsencaoConcurso,
        Instant instanteSolicitacao
    ) {
        validarPrazoEstritoDeIsencao(periodoIsencaoConcurso, instanteSolicitacao, "solicitar");
        if (this.status == StatusInscricao.HOMOLOGADA || this.status == StatusInscricao.CANCELADA) {
            throw new RegraDeNegocioException("Nao e permitido solicitar isencao para inscricao com status " + status);
        }
        if (this.pedidoIsencao != null) {
            throw new RegraDeNegocioException("Ja existe pedido de isencao registrado para esta inscricao.");
        }
        this.pedidoIsencao = new PedidoIsencao(justificativaLegal, instanteSolicitacao);
        this.status = StatusInscricao.ISENCAO_EM_ANALISE;
        return this.pedidoIsencao;
    }

    /**
     * Sobrecarga parametrizada pelas regras completas do edital via Specification Pattern.
     */
    public PedidoIsencao solicitarIsencaoComEspecificacao(
        EspecificacaoElegibilidadeIsencao.ContextoSolicitacaoIsencao contextoElegibilidade
    ) {
        Objects.requireNonNull(contextoElegibilidade, "Contexto de elegibilidade da isencao obrigatorio.");
        EspecificacaoElegibilidadeIsencao.validarOuRejeitar(contextoElegibilidade);
        return solicitarIsencao(
            contextoElegibilidade.justificativaLegal(),
            contextoElegibilidade.periodoIsencaoEdital(),
            contextoElegibilidade.instanteSolicitacao()
        );
    }

    /**
     * Regra de Negocio: O pedido de isencao so pode ser avaliado se a data atual
     * estiver estritamente dentro do Periodo de isencao/avaliacao configurado.
     */
    public void deferirIsencao(String parecer, Periodo periodoIsencaoConcurso, Instant instanteAvaliacao) {
        validarPrazoEstritoDeIsencao(periodoIsencaoConcurso, instanteAvaliacao, "avaliar (deferir)");
        exigirPedidoIsencaoExistente();
        this.pedidoIsencao.deferir(parecer, instanteAvaliacao);
        this.status = StatusInscricao.HOMOLOGADA;
        registerEvent(IsencaoJulgada.novo(
            this.id, this.numero, this.candidatoId, this.concursoId, true, this.pedidoIsencao.parecerAvaliacao(), instanteAvaliacao
        ));
    }

    public void indeferirIsencao(String parecer, Periodo periodoIsencaoConcurso, Instant instanteAvaliacao) {
        validarPrazoEstritoDeIsencao(periodoIsencaoConcurso, instanteAvaliacao, "avaliar (indeferir)");
        exigirPedidoIsencaoExistente();
        this.pedidoIsencao.indeferir(parecer, instanteAvaliacao);
        this.status = StatusInscricao.AGUARDANDO_PAGAMENTO;
        registerEvent(IsencaoJulgada.novo(
            this.id, this.numero, this.candidatoId, this.concursoId, false, this.pedidoIsencao.parecerAvaliacao(), instanteAvaliacao
        ));
    }

    public void cancelar() {
        if (this.status == StatusInscricao.CANCELADA) {
            throw new RegraDeNegocioException("A inscricao ja se encontra cancelada.");
        }
        this.status = StatusInscricao.CANCELADA;
    }

    public boolean estaHomologada() {
        return this.status == StatusInscricao.HOMOLOGADA;
    }

    private static void validarPrazoEstritoDeIsencao(Periodo periodoIsencao, Instant instanteAtual, String acao) {
        Objects.requireNonNull(periodoIsencao, "Periodo de isencao obrigatorio.");
        Objects.requireNonNull(instanteAtual, "Instante atual obrigatorio.");
        if (!periodoIsencao.contemEstritamente(instanteAtual)) {
            throw new PrazoIsencaoEncerradoException(
                "Nao e permitido " + acao + " pedido de isencao fora do periodo estrito estabelecido no edital."
            );
        }
    }

    private void exigirPedidoIsencaoExistente() {
        if (this.pedidoIsencao == null) {
            throw new RegraDeNegocioException("Esta inscricao nao possui pedido de isencao registrado.");
        }
    }

    @Override
    public InscricaoId id() {
        return id;
    }

    public NumeroInscricao numero() {
        return numero;
    }

    public CandidatoId candidatoId() {
        return candidatoId;
    }

    public ConcursoId concursoId() {
        return concursoId;
    }

    public CargoId cargoId() {
        return cargoId;
    }

    public StatusInscricao status() {
        return status;
    }

    public Boleto boleto() {
        return boleto;
    }

    public PedidoIsencao pedidoIsencao() {
        return pedidoIsencao;
    }
}
