package br.com.keyfalls.shared.infrastructure.event;

import br.com.keyfalls.shared.application.event.EventoProcessadoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementacao PostgreSQL real para controle de idempotencia de eventos consumidos.
 */
@Repository
public class EventoProcessadoJdbcAdapter implements EventoProcessadoRepository {

    private final JdbcTemplate jdbcTemplate;

    public EventoProcessadoJdbcAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    }

    @Override
    public boolean jaFoiProcessado(UUID eventId, String nomeConsumidor) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM shared_events.eventos_processados WHERE event_id = ? AND consumidor = ?",
            Integer.class,
            eventId,
            nomeConsumidor
        );
        return count != null && count > 0;
    }

    @Override
    public void registrarProcessamento(UUID eventId, String nomeConsumidor, Instant processadoEm) {
        jdbcTemplate.update(
            """
            INSERT INTO shared_events.eventos_processados (event_id, consumidor, processado_em)
            VALUES (?, ?, ?)
            ON CONFLICT (event_id, consumidor) DO NOTHING
            """,
            eventId,
            nomeConsumidor,
            Timestamp.from(processadoEm)
        );
    }
}
