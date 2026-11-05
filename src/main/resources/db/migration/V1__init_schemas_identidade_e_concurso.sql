-- ============================================================================
-- V1: Criacao dos Schemas por Bounded Context + Tabelas de Identidade e Concurso
-- Regra de Governanca: Foreign Keys permitidas APENAS entre tabelas do MESMO schema.
-- Referencias entre Bounded Contexts distintos usam UUID indexado sem FK fisica.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS shared;
CREATE SCHEMA IF NOT EXISTS identidade;
CREATE SCHEMA IF NOT EXISTS concurso;
CREATE SCHEMA IF NOT EXISTS inscricao;
CREATE SCHEMA IF NOT EXISTS logistica;
CREATE SCHEMA IF NOT EXISTS apuracao;
CREATE SCHEMA IF NOT EXISTS recurso;
CREATE SCHEMA IF NOT EXISTS comunicacao;

-- ----------------------------------------------------------------------------
-- 1. INFRAESTRUTURA COMPARTILHADA: Idempotent Consumer / Inbox (shared)
-- ----------------------------------------------------------------------------
CREATE TABLE shared.eventos_processados (
    event_id        UUID            NOT NULL,
    nome_consumidor VARCHAR(120)    NOT NULL,
    processado_em   TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_eventos_processados PRIMARY KEY (event_id, nome_consumidor)
);

-- ----------------------------------------------------------------------------
-- 2. BOUNDED CONTEXT: IDENTIDADE (schema: identidade)
-- ----------------------------------------------------------------------------
CREATE TABLE identidade.usuarios (
    id                  UUID            NOT NULL,
    nome_completo       VARCHAR(200)    NOT NULL,
    cpf                 CHAR(11)        NOT NULL,
    email               VARCHAR(180)    NOT NULL,
    senha_hash          VARCHAR(255)    NOT NULL,
    conta_bloqueada     BOOLEAN         NOT NULL DEFAULT FALSE,
    logradouro          VARCHAR(200)    NOT NULL,
    numero              VARCHAR(30)     NOT NULL,
    bairro              VARCHAR(120)    NOT NULL,
    cidade              VARCHAR(120)    NOT NULL,
    uf                  CHAR(2)         NOT NULL,
    cep                 CHAR(8)         NOT NULL,
    latitude            DOUBLE PRECISION,
    longitude           DOUBLE PRECISION,
    credencial_atualizada_em TIMESTAMPTZ NOT NULL,
    criado_em           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_identidade_usuarios PRIMARY KEY (id),
    CONSTRAINT uq_identidade_usuarios_cpf UNIQUE (cpf),
    CONSTRAINT uq_identidade_usuarios_email UNIQUE (email)
);

CREATE TABLE identidade.usuario_roles (
    usuario_id  UUID        NOT NULL,
    role        VARCHAR(30) NOT NULL,
    CONSTRAINT pk_identidade_usuario_roles PRIMARY KEY (usuario_id, role),
    CONSTRAINT fk_usuario_roles_usuario
        FOREIGN KEY (usuario_id) REFERENCES identidade.usuarios (id) ON DELETE CASCADE,
    CONSTRAINT ck_identidade_role_valida
        CHECK (role IN ('ROLE_CANDIDATO', 'ROLE_FISCAL', 'ROLE_ADMIN'))
);

-- ----------------------------------------------------------------------------
-- 3. BOUNDED CONTEXT: CONCURSO (schema: concurso)
-- ----------------------------------------------------------------------------
CREATE TABLE concurso.concursos (
    id                          UUID            NOT NULL,
    versao                      BIGINT          NOT NULL DEFAULT 0,
    codigo_edital               VARCHAR(50)     NOT NULL,
    titulo                      VARCHAR(255)    NOT NULL,
    abrangencia                 VARCHAR(20)     NOT NULL,
    status                      VARCHAR(40)     NOT NULL DEFAULT 'EDITAL_ABERTO',
    periodo_inscricao_inicio    TIMESTAMPTZ     NOT NULL,
    periodo_inscricao_fim       TIMESTAMPTZ     NOT NULL,
    periodo_isencao_inicio      TIMESTAMPTZ     NOT NULL,
    periodo_isencao_fim         TIMESTAMPTZ     NOT NULL,
    politica_anulacao           VARCHAR(40)     NOT NULL,
    configuracao_regras_edital  JSONB           NOT NULL DEFAULT '{}'::jsonb,
    criado_em                   TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_concurso_concursos PRIMARY KEY (id),
    CONSTRAINT uq_concurso_codigo_edital UNIQUE (codigo_edital),
    CONSTRAINT ck_concurso_abrangencia
        CHECK (abrangencia IN ('NACIONAL', 'ESTADUAL', 'MUNICIPAL')),
    CONSTRAINT ck_concurso_politica_anulacao
        CHECK (politica_anulacao IN ('PONTO_UNIVERSAL_STRATEGY', 'REDISTRIBUICAO_PESO_STRATEGY', 'PONTO_PARA_TODOS', 'DESCONSIDERAR_DO_TOTAL_DA_PROVA')),
    CONSTRAINT ck_concurso_periodo_inscricao
        CHECK (periodo_inscricao_inicio < periodo_inscricao_fim),
    CONSTRAINT ck_concurso_periodo_isencao
        CHECK (periodo_isencao_inicio < periodo_isencao_fim)
);

CREATE TABLE concurso.cargos (
    id              UUID            NOT NULL,
    concurso_id     UUID            NOT NULL,
    nome            VARCHAR(180)    NOT NULL,
    vagas           INTEGER         NOT NULL,
    taxa_inscricao  NUMERIC(10, 2)  NOT NULL,
    CONSTRAINT pk_concurso_cargos PRIMARY KEY (id),
    CONSTRAINT fk_cargos_concurso
        FOREIGN KEY (concurso_id) REFERENCES concurso.concursos (id) ON DELETE CASCADE,
    CONSTRAINT uq_cargo_nome_por_concurso UNIQUE (concurso_id, nome),
    CONSTRAINT ck_cargo_vagas_positivas CHECK (vagas > 0),
    CONSTRAINT ck_cargo_taxa_nao_negativa CHECK (taxa_inscricao >= 0)
);

CREATE TABLE concurso.provas (
    id                  UUID            NOT NULL,
    concurso_id         UUID            NOT NULL,
    cargo_id            UUID            NOT NULL,
    disciplina          VARCHAR(150)    NOT NULL,
    peso                NUMERIC(6, 2)   NOT NULL,
    gabarito_publicado  BOOLEAN         NOT NULL DEFAULT FALSE,
    gabarito_definitivo BOOLEAN         NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_concurso_provas PRIMARY KEY (id),
    CONSTRAINT fk_provas_concurso
        FOREIGN KEY (concurso_id) REFERENCES concurso.concursos (id) ON DELETE CASCADE,
    CONSTRAINT fk_provas_cargo
        FOREIGN KEY (cargo_id) REFERENCES concurso.cargos (id) ON DELETE CASCADE,
    CONSTRAINT ck_prova_peso_positivo CHECK (peso > 0)
);

CREATE TABLE concurso.questoes (
    id                      UUID        NOT NULL,
    prova_id                UUID        NOT NULL,
    numero_ordem            INTEGER     NOT NULL,
    enunciado               TEXT        NOT NULL,
    anulada                 BOOLEAN     NOT NULL DEFAULT FALSE,
    justificativa_anulacao  TEXT,
    resposta_gabarito       CHAR(1),
    CONSTRAINT pk_concurso_questoes PRIMARY KEY (id),
    CONSTRAINT fk_questoes_prova
        FOREIGN KEY (prova_id) REFERENCES concurso.provas (id) ON DELETE CASCADE,
    CONSTRAINT uq_questao_ordem_por_prova UNIQUE (prova_id, numero_ordem),
    CONSTRAINT ck_questao_alternativa_gabarito
        CHECK (resposta_gabarito IS NULL OR resposta_gabarito IN ('A', 'B', 'C', 'D', 'E'))
);

CREATE TABLE concurso.questao_opcoes (
    questao_id  UUID    NOT NULL,
    letra       CHAR(1) NOT NULL,
    texto       TEXT    NOT NULL,
    CONSTRAINT pk_concurso_questao_opcoes PRIMARY KEY (questao_id, letra),
    CONSTRAINT fk_opcoes_questao
        FOREIGN KEY (questao_id) REFERENCES concurso.questoes (id) ON DELETE CASCADE,
    CONSTRAINT ck_opcao_letra CHECK (letra IN ('A', 'B', 'C', 'D', 'E'))
);
