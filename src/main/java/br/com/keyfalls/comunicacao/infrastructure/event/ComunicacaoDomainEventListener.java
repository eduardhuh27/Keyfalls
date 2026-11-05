package br.com.keyfalls.comunicacao.infrastructure.event;

import br.com.keyfalls.apuracao.domain.event.ResultadoFinalPublicado;
import br.com.keyfalls.comunicacao.application.NotificacaoCandidatoEventHandler;
import br.com.keyfalls.inscricao.domain.event.InscricaoRealizada;
import br.com.keyfalls.inscricao.domain.event.IsencaoJulgada;
import br.com.keyfalls.logistica.domain.event.SalaAlocada;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Adapter de entrada de eventos (Inbound Event Adapter) no contexto Comunicacao.
 * Dispara somente apos o COMMIT da transacao produtora (AFTER_COMMIT) em nova transacao
 * isolada (REQUIRES_NEW) para persistir o controle de idempotencia com seguranca.
 */
@Component
public class ComunicacaoDomainEventListener {

    private final NotificacaoCandidatoEventHandler handler;

    public ComunicacaoDomainEventListener(NotificacaoCandidatoEventHandler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIsencaoJulgada(IsencaoJulgada evento) {
        handler.tratarIsencaoJulgada(evento);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInscricaoRealizada(InscricaoRealizada evento) {
        handler.tratarInscricaoRealizada(evento);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSalaAlocada(SalaAlocada evento) {
        handler.tratarSalaAlocada(evento);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onResultadoFinalPublicado(ResultadoFinalPublicado evento) {
        handler.tratarResultadoFinalPublicado(evento);
    }
}
