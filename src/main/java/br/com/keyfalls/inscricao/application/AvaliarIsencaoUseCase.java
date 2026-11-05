package br.com.keyfalls.inscricao.application;

import br.com.keyfalls.inscricao.domain.Inscricao;
import br.com.keyfalls.inscricao.domain.InscricaoRepository;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.Periodo;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Caso de Uso demonstrando o padrao canonico: mutar Agregado -> persistir -> pullDomainEvents() -> publicar.
 */
public final class AvaliarIsencaoUseCase {

    private final InscricaoRepository inscricaoRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public AvaliarIsencaoUseCase(
        InscricaoRepository inscricaoRepository,
        DomainEventPublisher eventPublisher,
        Clock clock
    ) {
        this.inscricaoRepository = Objects.requireNonNull(inscricaoRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public void executar(
        InscricaoId inscricaoId,
        boolean deferir,
        String parecerFundamentado,
        Periodo periodoIsencaoVigente
    ) {
        Inscricao inscricao = inscricaoRepository.buscarPorId(inscricaoId)
            .orElseThrow(() -> new RegraDeNegocioException("Inscricao nao encontrada: " + inscricaoId.valor()));

        Instant agora = Instant.now(clock);
        if (deferir) {
            inscricao.deferirIsencao(parecerFundamentado, periodoIsencaoVigente, agora);
        } else {
            inscricao.indeferirIsencao(parecerFundamentado, periodoIsencaoVigente, agora);
        }

        // 1. Persiste o estado do agregado dentro da transacao local
        inscricaoRepository.salvar(inscricao);

        // 2. Drena e despacha os eventos registrados (disparados em AFTER_COMMIT pelo adapter)
        eventPublisher.publishAll(inscricao.pullDomainEvents());
    }
}
