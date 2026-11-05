-- ============================================================================
-- V4: Fechamento completo do schema multi-contexto (Logistica, Apuracao,
-- Inscricao.isencao_id e Auditoria de Comunicacao)
-- ============================================================================

ALTER TABLE inscricao.inscricoes
    ADD COLUMN IF NOT EXISTS isencao_id UUID UNIQUE;

CREATE TABLE IF NOT EXISTS logistica.locais_de_prova (
    id                  UUID            NOT NULL,
    concurso_id         UUID            NOT NULL,
    nome_instituicao    VARCHAR(200)    NOT NULL,
    logradouro          VARCHAR(200)    NOT NULL,
    numero              VARCHAR(30)     NOT NULL,
    bairro              VARCHAR(120)    NOT NULL,
    cidade              VARCHAR(120)    NOT NULL,
    uf                  CHAR(2)         NOT NULL,
    cep                 CHAR(8)         NOT NULL,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    CONSTRAINT pk_logistica_locais PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_locais_concurso ON logistica.locais_de_prova (concurso_id);

ALTER TABLE logistica.salas
    ADD COLUMN IF NOT EXISTS nome_fiscal VARCHAR(180) NOT NULL DEFAULT 'Fiscal Designado',
    ADD COLUMN IF NOT EXISTS cpf_fiscal VARCHAR(11) NOT NULL DEFAULT '52998224725',
    ADD COLUMN IF NOT EXISTS inscricoes_alocadas_csv TEXT NOT NULL DEFAULT '';

CREATE TABLE IF NOT EXISTS logistica.alocacoes_de_sala (
    id                      UUID            NOT NULL,
    inscricao_id            UUID            NOT NULL,
    cargo_id                UUID            NOT NULL,
    local_de_prova_id       UUID            NOT NULL,
    sala_id                 UUID            NOT NULL,
    presente                BOOLEAN,
    registrado_em           TIMESTAMPTZ,
    hash_assinatura         VARCHAR(255),
    documento_apresentado   VARCHAR(60),
    CONSTRAINT pk_logistica_alocacoes PRIMARY KEY (id),
    CONSTRAINT uq_alocacao_inscricao UNIQUE (inscricao_id)
);

CREATE INDEX IF NOT EXISTS idx_alocacoes_sala ON logistica.alocacoes_de_sala (sala_id);

CREATE TABLE IF NOT EXISTS apuracao.fichas_de_resposta (
    id              UUID            NOT NULL,
    inscricao_id    UUID            NOT NULL,
    concurso_id     UUID            NOT NULL,
    capturada_em    TIMESTAMPTZ     NOT NULL,
    respostas_texto TEXT            NOT NULL,
    CONSTRAINT pk_apuracao_fichas PRIMARY KEY (id),
    CONSTRAINT uq_ficha_inscricao UNIQUE (inscricao_id)
);

CREATE INDEX IF NOT EXISTS idx_fichas_concurso ON apuracao.fichas_de_resposta (concurso_id);

CREATE TABLE IF NOT EXISTS apuracao.resultados (
    id                          UUID            NOT NULL,
    inscricao_id                UUID            NOT NULL,
    concurso_id                 UUID            NOT NULL,
    cargo_id                    UUID            NOT NULL,
    notas_por_prova_texto       TEXT            NOT NULL,
    nota_total_ponderada        NUMERIC(5, 2)   NOT NULL,
    percentual_total_ponderado  NUMERIC(5, 2)   NOT NULL,
    total_acertos               INTEGER         NOT NULL,
    total_erros                 INTEGER         NOT NULL,
    situacao                    VARCHAR(30)     NOT NULL,
    CONSTRAINT pk_apuracao_resultados PRIMARY KEY (id),
    CONSTRAINT uq_resultado_inscricao UNIQUE (inscricao_id)
);

CREATE INDEX IF NOT EXISTS idx_resultados_ranking
    ON apuracao.resultados (concurso_id, cargo_id, situacao, nota_total_ponderada DESC, total_acertos DESC);

CREATE TABLE IF NOT EXISTS apuracao.anulacoes_processadas (
    prova_id        UUID        NOT NULL,
    questao_id      UUID        NOT NULL,
    event_id        UUID        NOT NULL,
    aplicada_em     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_apuracao_anulacoes PRIMARY KEY (prova_id, questao_id)
);

CREATE TABLE IF NOT EXISTS comunicacao.historico_notificacoes (
    id                  UUID            NOT NULL,
    destinatario_email  VARCHAR(255)    NOT NULL,
    assunto             VARCHAR(255)    NOT NULL,
    corpo               TEXT            NOT NULL,
    enviado_em          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_comunicacao_notificacoes PRIMARY KEY (id)
);
