-- ============================================================================
-- V2: Estrutura do Bounded Context INSCRICAO (schema: inscricao)
-- Observe a ausencia intencional de REFERENCES para identidade.usuarios ou
-- concurso.concursos / concurso.cargos, preservando autonomia de banco de dados.
-- ============================================================================

CREATE TABLE inscricao.inscricoes (
    id                      UUID            NOT NULL,
    versao                  BIGINT          NOT NULL DEFAULT 0,
    numero_inscricao        VARCHAR(20)     NOT NULL,
    candidato_id            UUID            NOT NULL,
    concurso_id             UUID            NOT NULL,
    cargo_id                UUID            NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    boleto_linha_digitavel  VARCHAR(64),
    boleto_valor            NUMERIC(10, 2),
    boleto_vencimento       DATE,
    boleto_pago             BOOLEAN         NOT NULL DEFAULT FALSE,
    boleto_liquidado_em     TIMESTAMPTZ,
    isencao_justificativa   TEXT,
    isencao_solicitada_em   TIMESTAMPTZ,
    isencao_situacao        VARCHAR(20),
    isencao_parecer         TEXT,
    isencao_avaliada_em     TIMESTAMPTZ,
    CONSTRAINT pk_inscricao_inscricoes PRIMARY KEY (id),
    CONSTRAINT uq_inscricao_numero UNIQUE (numero_inscricao)
);

CREATE UNIQUE INDEX uq_inscricoes_candidato_concurso_ativa
    ON inscricao.inscricoes (candidato_id, concurso_id)
    WHERE status <> 'CANCELADA';

CREATE INDEX idx_inscricoes_concurso_status
    ON inscricao.inscricoes (concurso_id, status);
