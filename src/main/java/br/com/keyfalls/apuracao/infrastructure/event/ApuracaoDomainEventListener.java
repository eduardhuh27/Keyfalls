package br.com.keyfalls.apuracao.infrastructure.event;

import br.com.keyfalls.apuracao.application.RecalculoPorQuestaoAnuladaHandler;
import br.com.keyfalls.concurso.domain.event.QuestaoAnulada;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * Adapter de entrada de eventos (Inbound Event Adapter) no contexto Apuracao.
 * Reage a QuestaoAnulada somente apos o commit da transacao de origem (AFTER_COMMIT),
 * abrindo uma nova transacao local (REQUIRES_NEW) para persistir o lote de resultados
 * recalculados, gravar as chaves de idempotencia e publicar ResultadoRepublicado.
 */
@Component
public class ApuracaoDomainEventListener {

    private final RecalculoPorQuestaoAnuladaHandler handler;

    public ApuracaoDomainEventListener(RecalculoPorQuestaoAnuladaHandler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onQuestaoAnulada(QuestaoAnulada evento) {
        handler.tratarQuestaoAnulada(evento);
    }
}
