package br.com.keyfalls.comunicacao.infrastructure.config;

import br.com.keyfalls.comunicacao.application.ConsultaDestinatarioPort;
import br.com.keyfalls.comunicacao.application.NotificacaoCandidatoEventHandler;
import br.com.keyfalls.comunicacao.application.NotificadorEmailPort;
import br.com.keyfalls.shared.application.event.EventoProcessadoRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configuracao de Beans para o Bounded Context Comunicacao.
 */
@Configuration
public class ComunicacaoBeanConfiguration {

    @Bean
    public NotificacaoCandidatoEventHandler notificacaoCandidatoEventHandler(
        NotificadorEmailPort notificadorEmailPort,
        ConsultaDestinatarioPort consultaDestinatarioPort,
        EventoProcessadoRepository eventoProcessadoRepository
    ) {
        return new NotificacaoCandidatoEventHandler(
            notificadorEmailPort,
            consultaDestinatarioPort,
            eventoProcessadoRepository,
            Clock.systemUTC()
        );
    }
}
