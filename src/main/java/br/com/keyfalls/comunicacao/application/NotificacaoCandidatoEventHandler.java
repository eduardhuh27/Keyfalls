package br.com.keyfalls.comunicacao.application;

import br.com.keyfalls.apuracao.domain.event.ResultadoFinalPublicado;
import br.com.keyfalls.comunicacao.domain.EnviadorEmailPort;
import br.com.keyfalls.comunicacao.domain.MensagemTransacional;
import br.com.keyfalls.inscricao.domain.event.InscricaoRealizada;
import br.com.keyfalls.inscricao.domain.event.IsencaoJulgada;
import br.com.keyfalls.logistica.domain.event.SalaAlocada;
import br.com.keyfalls.shared.application.event.EventoProcessadoRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Handler de aplicacao do contexto Comunicacao (Fluxos 1 e 3),
 * 100% livre de dependencias de Spring/mensageria e protegido por idempotencia.
 */
public final class NotificacaoCandidatoEventHandler {

    private static final String CONSUMIDOR_ISENCAO = "comunicacao.isencao-julgada";
    private static final String CONSUMIDOR_INSCRICAO = "comunicacao.inscricao-realizada";
    private static final String CONSUMIDOR_SALA = "comunicacao.sala-alocada";
    private static final String CONSUMIDOR_RESULTADO_FINAL = "comunicacao.resultado-final";

    private final EnviadorEmailPort enviadorEmail;
    private final ConsultaDestinatarioPort consultaDestinatario;
    private final EventoProcessadoRepository eventoProcessadoRepository;
    private final Clock clock;

    public NotificacaoCandidatoEventHandler(
        EnviadorEmailPort enviadorEmail,
        ConsultaDestinatarioPort consultaDestinatario,
        EventoProcessadoRepository eventoProcessadoRepository,
        Clock clock
    ) {
        this.enviadorEmail = Objects.requireNonNull(enviadorEmail);
        this.consultaDestinatario = Objects.requireNonNull(consultaDestinatario);
        this.eventoProcessadoRepository = Objects.requireNonNull(eventoProcessadoRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Fluxo 1: Notificacao transacional do julgamento de isencao (deferida/indeferida + motivo).
     */
    public void tratarIsencaoJulgada(IsencaoJulgada evento) {
        Objects.requireNonNull(evento, "Evento IsencaoJulgada obrigatorio.");
        if (eventoProcessadoRepository.jaFoiProcessado(evento.eventId(), CONSUMIDOR_ISENCAO)) {
            return;
        }

        var destinatario = consultaDestinatario.buscarPorCandidatoId(evento.candidatoId());
        String resultadoDecisao = evento.deferida() ? "DEFERIDO" : "INDEFERIDO";
        String assunto = "[KeyFalls] Resultado do Pedido de Isencao - Inscricao " + evento.numeroInscricao().codigo();
        String corpo = """
            Ola, %s.
            Seu pedido de isencao para a inscricao %s foi julgado como %s.
            Parecer/Motivo da Banca: %s
            """.formatted(
            destinatario.nomeCompleto(),
            evento.numeroInscricao().codigo(),
            resultadoDecisao,
            evento.motivoDecisao()
        );

        enviadorEmail.enviar(new MensagemTransacional(destinatario.email(), assunto, corpo));
        eventoProcessadoRepository.registrarProcessamento(evento.eventId(), CONSUMIDOR_ISENCAO, Instant.now(clock));
    }

    /**
     * Fluxo 3a: Confirmacao de inscricao realizada.
     */
    public void tratarInscricaoRealizada(InscricaoRealizada evento) {
        Objects.requireNonNull(evento, "Evento InscricaoRealizada obrigatorio.");
        if (eventoProcessadoRepository.jaFoiProcessado(evento.eventId(), CONSUMIDOR_INSCRICAO)) {
            return;
        }

        var destinatario = consultaDestinatario.buscarPorCandidatoId(evento.candidatoId());
        String assunto = "[KeyFalls] Inscricao Recebida - Protocolo " + evento.numeroInscricao().codigo();
        String corpo = "Ola, %s. Sua inscricao (%s) foi registrada com sucesso."
            .formatted(destinatario.nomeCompleto(), evento.numeroInscricao().codigo());

        enviadorEmail.enviar(new MensagemTransacional(destinatario.email(), assunto, corpo));
        eventoProcessadoRepository.registrarProcessamento(evento.eventId(), CONSUMIDOR_INSCRICAO, Instant.now(clock));
    }

    /**
     * Fluxo 3b: Convocacao e aviso de ensalamento (SalaAlocada).
     */
    public void tratarSalaAlocada(SalaAlocada evento) {
        Objects.requireNonNull(evento, "Evento SalaAlocada obrigatorio.");
        if (eventoProcessadoRepository.jaFoiProcessado(evento.eventId(), CONSUMIDOR_SALA)) {
            return;
        }

        var destinatario = consultaDestinatario.buscarPorInscricaoId(evento.inscricaoId());
        var local = consultaDestinatario.buscarDetalhesSala(evento.localDeProvaId(), evento.salaId());

        String assunto = "[KeyFalls] Cartao de Convocacao - Local e Sala de Prova";
        String corpo = "Ola, %s. Voce foi alocado em %s (Sala %s) - Endereco: %s."
            .formatted(destinatario.nomeCompleto(), local.nomeInstituicao(), local.identificacaoSala(), local.enderecoFormatado());

        enviadorEmail.enviar(new MensagemTransacional(destinatario.email(), assunto, corpo));
        eventoProcessadoRepository.registrarProcessamento(evento.eventId(), CONSUMIDOR_SALA, Instant.now(clock));
    }

    /**
     * Fluxo 3c: Aviso de homologacao e publicacao do resultado final.
     */
    public void tratarResultadoFinalPublicado(ResultadoFinalPublicado evento) {
        Objects.requireNonNull(evento, "Evento ResultadoFinalPublicado obrigatorio.");
        if (eventoProcessadoRepository.jaFoiProcessado(evento.eventId(), CONSUMIDOR_RESULTADO_FINAL)) {
            return;
        }

        List<ConsultaDestinatarioPort.DadosContatoCandidato> destinatarios =
            consultaDestinatario.listarCandidatosHomologadosDoCargo(evento.concursoId(), evento.cargoId());

        String assunto = "[KeyFalls] Resultado Final Publicado";
        for (var destinatario : destinatarios) {
            String corpo = "Ola, %s. O resultado final oficial do concurso foi publicado (%d classificados)."
                .formatted(destinatario.nomeCompleto(), evento.totalClassificadosHomologados());
            enviadorEmail.enviar(new MensagemTransacional(destinatario.email(), assunto, corpo));
        }

        eventoProcessadoRepository.registrarProcessamento(
            evento.eventId(), CONSUMIDOR_RESULTADO_FINAL, Instant.now(clock)
        );
    }
}
