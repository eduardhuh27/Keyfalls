-- ============================================================================
-- V4: Fechamento completo do schema multi-contexto (Identidade, Concurso,
-- Logistica, Apuracao, Inscricao e Auditoria de Comunicacao)
-- ============================================================================

-- 1. IDENTIDADE: alinhamento de tipos VARCHAR e coluna ativo para Hibernate validate
ALTER TABLE identidade.usuarios
    ALTER COLUMN cpf TYPE VARCHAR(11),
    ALTER COLUMN email TYPE VARCHAR(255),
    ALTER COLUMN uf TYPE VARCHAR(2),
    ALTER COLUMN cep TYPE VARCHAR(8),
    ALTER COLUMN credencial_atualizada_em SET DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS ativo BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE identidade.usuario_roles
    ALTER COLUMN role TYPE VARCHAR(40);

-- 2. CONCURSO: colunas complementares usadas pelos adaptadores JDBC
ALTER TABLE concurso.concursos
    ADD COLUMN IF NOT EXISTS homologado_em TIMESTAMPTZ;

ALTER TABLE concurso.provas
    ADD COLUMN IF NOT EXISTS gabarito_publicado_em TIMESTAMPTZ;

ALTER TABLE concurso.questoes
    ADD COLUMN IF NOT EXISTS alternativas_json JSONB NOT NULL DEFAULT '{}'::jsonb;

-- 3. INSCRICAO
ALTER TABLE inscricao.inscricoes
    ADD COLUMN IF NOT EXISTS isencao_id UUID UNIQUE;

-- 4. LOGISTICA
CREATE TABLE IF NOT EXISTS logistica.locais_de_prova (
    id                  UUID            NOT NULL,
    versao              BIGINT          NOT NULL DEFAULT 0,
    concurso_id         UUID            NOT NULL,
    nome_instituicao    VARCHAR(200)    NOT NULL,
    logradouro          VARCHAR(200)    NOT NULL,
    numero              VARCHAR(30)     NOT NULL,
    bairro              VARCHAR(120)    NOT NULL,
    cidade              VARCHAR(120)    NOT NULL,
    uf                  VARCHAR(2)      NOT NULL,
    cep                 VARCHAR(8)      NOT NULL,
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
    alocado_em              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_logistica_alocacoes PRIMARY KEY (id),
    CONSTRAINT uq_alocacao_inscricao UNIQUE (inscricao_id)
);

CREATE INDEX IF NOT EXISTS idx_alocacoes_sala ON logistica.alocacoes_de_sala (sala_id);

-- 5. APURACAO (tabelas normalizadas para fichas, marcacoes, resultados e notas por prova)
CREATE TABLE IF NOT EXISTS apuracao.fichas_de_resposta (
    id              UUID            NOT NULL,
    inscricao_id    UUID            NOT NULL,
    concurso_id     UUID            NOT NULL,
    capturada_em    TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_apuracao_fichas PRIMARY KEY (id),
    CONSTRAINT uq_ficha_inscricao UNIQUE (inscricao_id)
);

CREATE INDEX IF NOT EXISTS idx_fichas_concurso ON apuracao.fichas_de_resposta (concurso_id);

CREATE TABLE IF NOT EXISTS apuracao.respostas_marcadas (
    ficha_id            UUID        NOT NULL,
    prova_id            UUID        NOT NULL,
    questao_id          UUID        NOT NULL,
    alternativa_marcada VARCHAR(5),
    CONSTRAINT pk_apuracao_respostas_marcadas PRIMARY KEY (ficha_id, prova_id, questao_id),
    CONSTRAINT fk_respostas_ficha
        FOREIGN KEY (ficha_id) REFERENCES apuracao.fichas_de_resposta (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS apuracao.resultados (
    id                          UUID            NOT NULL,
    inscricao_id                UUID            NOT NULL,
    concurso_id                 UUID            NOT NULL,
    cargo_id                    UUID            NOT NULL,
    nota_total_ponderada        NUMERIC(5, 2)   NOT NULL,
    percentual_total_ponderado  NUMERIC(5, 2)   NOT NULL,
    total_acertos               INTEGER         NOT NULL,
    total_erros                 INTEGER         NOT NULL,
    situacao                    VARCHAR(30)     NOT NULL,
    calculado_em                TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_apuracao_resultados PRIMARY KEY (id),
    CONSTRAINT uq_resultado_inscricao UNIQUE (inscricao_id)
);

CREATE INDEX IF NOT EXISTS idx_resultados_ranking
    ON apuracao.resultados (concurso_id, cargo_id, situacao, nota_total_ponderada DESC, total_acertos DESC);

CREATE TABLE IF NOT EXISTS apuracao.notas_prova (
    resultado_id                UUID            NOT NULL,
    prova_id                    UUID            NOT NULL,
    peso                        NUMERIC(6, 2)   NOT NULL,
    acertos                     INTEGER         NOT NULL,
    erros                       INTEGER         NOT NULL,
    total_questoes_consideradas INTEGER         NOT NULL,
    percentual_acertos          NUMERIC(5, 2)   NOT NULL,
    nota_final_prova            NUMERIC(5, 2)   NOT NULL,
    CONSTRAINT pk_apuracao_notas_prova PRIMARY KEY (resultado_id, prova_id),
    CONSTRAINT fk_notas_prova_resultado
        FOREIGN KEY (resultado_id) REFERENCES apuracao.resultados (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS apuracao.anulacoes_processadas (
    prova_id        UUID        NOT NULL,
    questao_id      UUID        NOT NULL,
    event_id_origem UUID        NOT NULL,
    aplicado_em     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_apuracao_anulacoes PRIMARY KEY (prova_id, questao_id)
);

-- 6. COMUNICACAO
CREATE TABLE IF NOT EXISTS comunicacao.historico_notificacoes (
    id                  UUID            NOT NULL,
    destinatario_email  VARCHAR(255)    NOT NULL,
    assunto             VARCHAR(255)    NOT NULL,
    corpo               TEXT            NOT NULL,
    enviado_em          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_comunicacao_notificacoes PRIMARY KEY (id)
);
