package br.com.keyfalls.comunicacao.infrastructure.email;

import br.com.keyfalls.comunicacao.domain.EnviadorEmailPort;
import br.com.keyfalls.comunicacao.domain.MensagemTransacional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Adaptador Tecnico de Notificacao Transacional por E-mail (Secondary Adapter).
 * Despacha mensagens via JavaMailSender e persiste trilha de auditoria em comunicacao.historico_notificacoes.
 */
@Component
public class JavaMailNotificadorAdapter implements EnviadorEmailPort {

    private static final Logger log = LoggerFactory.getLogger(JavaMailNotificadorAdapter.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final JdbcTemplate jdbcTemplate;
    private final String remetentePadrao;

    public JavaMailNotificadorAdapter(
        ObjectProvider<JavaMailSender> mailSenderProvider,
        JdbcTemplate jdbcTemplate,
        @Value("${keyfalls.mail.from:no-reply@keyfalls.com.br}") String remetentePadrao
    ) {
        this.mailSenderProvider = Objects.requireNonNull(mailSenderProvider);
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
        this.remetentePadrao = remetentePadrao;
    }

    @Override
    public void enviar(MensagemTransacional mensagem) {
        Objects.requireNonNull(mensagem, "MensagemTransacional obrigatoria.");
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (mailSender != null) {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setFrom(remetentePadrao);
                mail.setTo(mensagem.destinatario().endereco());
                mail.setSubject(mensagem.assunto());
                mail.setText(mensagem.corpo());
                mailSender.send(mail);
            } catch (Exception ex) {
                log.warn(
                    "SMTP indisponivel no ambiente atual ao enviar para {} ({}). Registrando auditoria local.",
                    mensagem.destinatario().endereco(),
                    ex.getMessage()
                );
            }
        }

        jdbcTemplate.update(
            """
            INSERT INTO comunicacao.historico_notificacoes (
                id, destinatario_email, assunto, corpo, enviado_em
            ) VALUES (?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            mensagem.destinatario().endereco(),
            mensagem.assunto(),
            mensagem.corpo(),
            Timestamp.from(Instant.now())
        );

        log.info(
            "[NOTIFICACAO E-MAIL] Para={} | Assunto='{}'",
            mensagem.destinatario().endereco(),
            mensagem.assunto()
        );
    }
}
