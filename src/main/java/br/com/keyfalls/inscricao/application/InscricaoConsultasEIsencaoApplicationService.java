package br.com.keyfalls.inscricao.application;

import br.com.keyfalls.inscricao.domain.Boleto;
import br.com.keyfalls.inscricao.domain.Inscricao;
import br.com.keyfalls.inscricao.domain.InscricaoRepository;
import br.com.keyfalls.inscricao.domain.PedidoIsencao;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;
import br.com.keyfalls.shared.domain.exception.EntidadeNaoEncontradaException;
import br.com.keyfalls.shared.domain.exception.PropriedadeRecursoVioladaException;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementacao pura de aplicacao para consulta de boleto, solicitacao de isencao e consulta de parecer,
 * blindada contra IDOR (Insecure Direct Object Reference).
 */
public class InscricaoConsultasEIsencaoApplicationService implements InscricaoConsultasEIsencaoUseCases {

    private final InscricaoRepository inscricaoRepository;
    private final ConsultaOfertaConcursoPort consultaOfertaConcursoPort;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public InscricaoConsultasEIsencaoApplicationService(
        InscricaoRepository inscricaoRepository,
        ConsultaOfertaConcursoPort consultaOfertaConcursoPort,
        DomainEventPublisher eventPublisher,
        Clock clock
    ) {
        this.inscricaoRepository = Objects.requireNonNull(inscricaoRepository);
        this.consultaOfertaConcursoPort = Objects.requireNonNull(consultaOfertaConcursoPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public BoletoOutput consultarBoleto(UUID inscricaoId, UUID solicitanteId, boolean isAdmin) {
        Inscricao inscricao = carregarEValidarPropriedade(inscricaoId, solicitanteId, isAdmin);
        Boleto boleto = inscricao.boleto();
        if (boleto == null) {
            throw new EntidadeNaoEncontradaException("Nenhum boleto emitido para a inscricao: " + inscricaoId);
        }
        return new BoletoOutput(
            inscricao.id().valor(),
            inscricao.numero().codigo(),
            boleto.linhaDigitavel(),
            boleto.valor(),
            boleto.vencimento(),
            boleto.pago()
        );
    }

    @Override
    public IsencaoOutput solicitarIsencao(SolicitarIsencaoCommand command) {
        Objects.requireNonNull(command, "SolicitarIsencaoCommand obrigatorio.");
        Inscricao inscricao = carregarEValidarPropriedade(
            command.inscricaoId(),
            command.solicitanteAutenticadoId(),
            false
        );

        var oferta = consultaOfertaConcursoPort.consultarOferta(inscricao.concursoId(), inscricao.cargoId())
            .orElseThrow(() -> new RegraDeNegocioException("Dados do concurso nao encontrados para validacao de prazo."));

        Instant agora = Instant.now(clock);
        PedidoIsencao pedido = inscricao.solicitarIsencao(
            command.justificativaLegal(),
            oferta.periodoIsencao(),
            agora
        );

        inscricaoRepository.salvar(inscricao);
        inscricao.pullDomainEvents().forEach(eventPublisher::publish);

        return new IsencaoOutput(
            pedido.id().valor(),
            inscricao.id().valor(),
            pedido.situacao().name(),
            pedido.justificativaLegal(),
            pedido.parecerAvaliacao(),
            pedido.solicitadoEm(),
            pedido.avaliadoEm()
        );
    }

    @Override
    public IsencaoOutput consultarIsencao(UUID isencaoOuInscricaoId, UUID solicitanteId, boolean isAdmin) {
        Inscricao inscricao = carregarEValidarPropriedade(isencaoOuInscricaoId, solicitanteId, isAdmin);
        PedidoIsencao pedido = inscricao.pedidoIsencao();
        if (pedido == null) {
            throw new EntidadeNaoEncontradaException("Pedido de isencao nao encontrado: " + isencaoOuInscricaoId);
        }
        return new IsencaoOutput(
            pedido.id().valor(),
            inscricao.id().valor(),
            pedido.situacao().name(),
            pedido.justificativaLegal(),
            pedido.parecerAvaliacao(),
            pedido.solicitadoEm(),
            pedido.avaliadoEm()
        );
    }

    private Inscricao carregarEValidarPropriedade(UUID inscricaoId, UUID solicitanteId, boolean isAdmin) {
        Inscricao inscricao = inscricaoRepository.buscarPorId(new InscricaoId(inscricaoId))
            .orElseThrow(() -> new EntidadeNaoEncontradaException("Inscricao nao encontrada: " + inscricaoId));

        if (!isAdmin && !inscricao.candidatoId().valor().equals(solicitanteId)) {
            throw new PropriedadeRecursoVioladaException(
                "Acesso negado (anti-IDOR): o recurso solicitado pertence a outro candidato."
            );
        }
        return inscricao;
    }
}
