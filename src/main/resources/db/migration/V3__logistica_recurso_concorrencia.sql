-- ============================================================================
-- V3: Tabelas de controle concorrente (@Version) para Logistica (Salas),
-- Recurso (Julgamento e Homologacao) e Idempotencia de Eventos
-- ============================================================================

CREATE TABLE logistica.salas (
    id                  UUID            NOT NULL,
    versao              BIGINT          NOT NULL DEFAULT 0,
    local_de_prova_id   UUID            NOT NULL,
    identificacao       VARCHAR(60)     NOT NULL,
    capacidade_maxima   INTEGER         NOT NULL,
    vagas_ocupadas      INTEGER         NOT NULL DEFAULT 0,
    cargo_atendido_id   UUID            NOT NULL,
    CONSTRAINT pk_logistica_salas PRIMARY KEY (id),
    CONSTRAINT ck_logistica_salas_capacidade CHECK (capacidade_maxima > 0),
    CONSTRAINT ck_logistica_salas_ocupacao CHECK (vagas_ocupadas >= 0 AND vagas_ocupadas <= capacidade_maxima)
);

CREATE TABLE recurso.recursos (
    id                  UUID            NOT NULL,
    versao              BIGINT          NOT NULL DEFAULT 0,
    concurso_id         UUID            NOT NULL,
    prova_id            UUID            NOT NULL,
    questao_id          UUID            NOT NULL,
    candidato_id        UUID            NOT NULL,
    fundamentacao       TEXT            NOT NULL,
    hash_agrupamento    VARCHAR(64)     NOT NULL,
    decisao             VARCHAR(20),
    parecer_banca       TEXT,
    protocolado_em      TIMESTAMPTZ     NOT NULL,
    julgado_em          TIMESTAMPTZ,
    CONSTRAINT pk_recurso_recursos PRIMARY KEY (id),
    CONSTRAINT uq_recurso_questao_candidato UNIQUE (questao_id, candidato_id)
);

CREATE TABLE shared_events.eventos_processados (
    event_id        UUID            NOT NULL,
    consumidor      VARCHAR(100)    NOT NULL,
    processado_em   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_eventos_processados PRIMARY KEY (event_id, consumidor)
);
