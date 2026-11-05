# KeyFalls API — Plataforma de Gestão de Concursos Públicos

API RESTful corporativa construída em **Java 21** e **Spring Boot 3.3.5** para gestão ponta a ponta de concursos públicos, estruturada como um **Monolito Modular** orientado a **Domain-Driven Design (DDD Estratégico e Tático)**, **Clean Architecture**, **Hexagonal Architecture (Ports & Adapters)** e **Event-Driven Architecture (EDA)**.

---

## 1. Arquitetura e Bounded Contexts

O projeto está organizado sob o pacote raiz `br.com.keyfalls`, isolando estritamente **7 Bounded Contexts** + **Shared Kernel**:

| Contexto | Pacote | Responsabilidade Central |
| :--- | :--- | :--- |
| **Shared Kernel** | `br.com.keyfalls.shared` | Superclasses táticas (`AggregateRoot`, `DomainEvent`), 12 IDs fortemente tipados (`ConcursoId`, `InscricaoId`, `IsencaoId`, etc.), VOs compartilhados (`CPF`, `Email`, `Endereco`, `Peso`, `Percentual`, `Periodo`, `NumeroInscricao`) e infraestrutura transversal (JWT, OpenAPI, `ProblemDetail` RFC 7807). |
| **Concurso** | `br.com.keyfalls.concurso` | Ciclo de vida do certame (`Concurso`), `Cargo`, `Prova` (com `Peso`), `Questao` e publicação de `Gabarito` preliminar/definitivo (`GabaritoPublicado`). |
| **Identidade** | `br.com.keyfalls.identidade` | Cadastro de `Candidato`, gestão de `Credencial` (hash de senha) e autenticação stateless OAuth2/JWT (`ROLE_CANDIDATO`, `ROLE_FISCAL`, `ROLE_ADMIN`). |
| **Inscrição** | `br.com.keyfalls.inscricao` | Registro de `Inscricao`, emissão de `Boleto` (FEBRABAN) e fluxo completo de `PedidoIsencao` (`InscricaoRealizada`, `IsencaoJulgada`). |
| **Logística** | `br.com.keyfalls.logistica` | Credenciamento de `LocalDeProva`, `Sala` (homogeneidade de cargo + capacidade máxima com `@Version`), `Fiscal`, `AlocadorDeSalas` por proximidade geográfica (`SalaAlocada`) e `ListaDePresenca`. |
| **Apuração** | `br.com.keyfalls.apuracao` | Leitura óptica em streaming (`FichaDeResposta`), cálculo ponderado exato (`CalculadoraDeNota`, `NotaProva`, `Resultado`), ranking meritocrático com critérios de desempate (`ClassificadorDeCandidatos`) e recálculo automático pós-anulação (`FichasProcessadas`, `NotasCalculadas`, `ResultadoRepublicado`). |
| **Recurso** | `br.com.keyfalls.recurso` | Interposição e julgamento de `Recurso` contra questões (`QuestaoAnulada`) e trava de homologação final do concurso (`ResultadoFinalPublicado`). |
| **Comunicação** | `br.com.keyfalls.comunicacao` | Consumo assíncrono e idempotente de eventos de domínio para envio de notificações transacionais por e-mail (`JavaMailSender` + trilha de auditoria). |

### Estrutura Interna de Camadas por Contexto
```text
br.com.keyfalls.<contexto>
 ├─ domain         -> 100% Java 21 puro (Zero dependências de Spring, JPA/Hibernate, Jackson, Bean Validation ou Lombok)
 ├─ application    -> Casos de Uso (Inbound Ports & Application Services), Handlers de Eventos e Secondary Output Ports
 ├─ infrastructure -> Entidades JPA, Adaptadores PostgreSQL (JPA/JDBC), Parsers CSV, SMTP, Eventos AFTER_COMMIT e @Configuration
 └─ interfaces     -> Controllers REST, DTOs de Request/Response com Jakarta Bean Validation e documentação OpenAPI 3
```

---

## 2. Pré-requisitos

Certifique-se de ter instalado no ambiente:
- **JDK 21** (Eclipse Temurin 21 ou equivalente)
- **Apache Maven 3.9+**
- **Docker** e **Docker Compose** (necessários para subir o PostgreSQL 16, o Mailpit e para executar os testes de integração com Testcontainers)

---

## 3. Como Rodar o Projeto

Você pode executar a **API KeyFalls** de duas maneiras: **100% containerizada via Docker Compose** ou **localmente via Maven** com a infraestrutura em containers.

### Opção A: Rodando Tudo com Docker Compose (Recomendado)

Na raiz do projeto, execute:

```bash
docker compose up -d --build
```

Esse comando provisiona automaticamente:
1. **`keyfalls-postgres`** (`postgres:16-alpine`) na porta `5432` (banco `keyfalls`, usuário `keyfalls`, senha `keyfalls_secret`);
2. **`keyfalls-mailpit`** (`axllent/mailpit`) nas portas `1025` (SMTP) e `8025` (Web UI para visualizar os e-mails transacionais enviados pela API);
3. **`keyfalls-api`** (imagem multi-stage com JRE 21 customizada via `jlink` e ZGC Generational) na porta `8080`, aplicando automaticamente as migrations Flyway (`V1` a `V4`).

Para acompanhar os logs da aplicação:
```bash
docker compose logs -f keyfalls-api
```

### Opção B: Rodando a Infraestrutura no Docker e a API Localmente via Maven

1. Suba apenas o banco de dados PostgreSQL e o servidor SMTP Mailpit:
   ```bash
   docker compose up -d postgres mailpit
   ```
2. Execute a aplicação via Spring Boot Maven Plugin:
   ```bash
   mvn spring-boot:run -Dspring-boot.run.Arguments="--spring.mail.host=localhost --spring.mail.port=1025 --spring.mail.properties.mail.smtp.starttls.enable=false --spring.mail.properties.mail.smtp.starttls.required=false"
   ```

### Endereços Úteis com a Aplicação no Ar
- **Swagger UI (Documentação Interativa OpenAPI 3)**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **Especificação OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Healthcheck (Spring Boot Actuator)**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Mailpit Web UI (Caixa de Entrada de E-mails)**: [http://localhost:8025](http://localhost:8025)

---

## 4. Como Executar os Testes

O projeto conta com uma suíte de testes adversariais cobrindo regras de domínio, casos de borda aritméticos, coreografia de eventos, concorrência otimista, segurança RBAC/anti-IDOR e governança arquitetural com **ArchUnit**.

> **Nota**: Certifique-se de que o **Docker Desktop / Docker Engine** esteja em execução antes de rodar os testes de integração que utilizam **Testcontainers (`postgres:16-alpine`)**.

### 4.1. Executar Toda a Suíte de Testes
```bash
mvn clean test
```

### 4.2. Executar Suítes de Teste Específicas

1. **Governança Arquitetural (ArchUnit — Pureza do Domínio, Isolamento de Contextos e Camadas)**:
   ```bash
   mvn test -Dtest=KeyFallsArchitectureTest
   ```
2. **Testes Unitários de Domínio: Cálculo Ponderado, Dízimas Periódicas, Anulação e Desempate**:
   ```bash
   mvn test -Dtest=CalculadoraEClassificadorDomainTest
   ```
3. **Testes Unitários de Invariantes de Agregados e Casos de Borda**:
   ```bash
   mvn test -Dtest=RegrasDeAgregadoEdgeCasesTest
   ```
4. **Testes de Integração: Coreografia de Eventos (`AFTER_COMMIT`), Idempotência e Concorrência (`CountDownLatch` / `@Version`)**:
   ```bash
   mvn test -Dtest=FluxoEventosEConcorrenciaIntegrationTest
   ```
5. **Testes de Segurança (OAuth2/JWT RBAC), Anti-IDOR e Contratos REST (`ProblemDetail` RFC 7807)**:
   ```bash
   mvn test -Dtest=SegurancaEContratosApiTest
   ```

### 4.3. Validação Rápida de Compilação Pura (`domain` + `application`) com `javac`
Como as camadas `domain` e `application` são 100% livres de bibliotecas externas, você pode compilar todos os seus 121 arquivos diretamente com o compilador padrão do JDK 21:

```powershell
# PowerShell (Windows)
$files = Get-ChildItem -Path src\main\java\br\com\keyfalls -Recurse -Filter "*.java" |
         Where-Object { $_.FullName -match "\\(domain|application)\\" } |
         Select-Object -ExpandProperty FullName
javac --release 21 -Xlint:all -d target/pure-check $files
```

---

## 5. Tutorial Prático: Testando o Fluxo Completo via `curl` ou Swagger UI

### Passo 1: Cadastrar um Candidato e Obter o Token JWT (`ROLE_CANDIDATO`)
```bash
curl -X POST http://localhost:8080/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{
    "nomeCompleto": "Ana Carolina Silva",
    "cpf": "52998224725",
    "email": "ana.silva@email.com",
    "senha": "SenhaForte@2026",
    "logradouro": "Av. Paulista",
    "numero": "1000",
    "bairro": "Bela Vista",
    "cidade": "Sao Paulo",
    "uf": "SP",
    "cep": "01310100",
    "latitude": -23.5649,
    "longitude": -46.6520
  }'
```
> Copie o campo `accessToken` retornado para usar nas requisições autenticadas como candidato (`Authorization: Bearer <TOKEN>`). Para autenticar novamente no futuro, utilize `POST /auth/login`.

### Passo 2: Fluxo Administrativo de Concurso (`ROLE_ADMIN`)
Com um token de perfil `ROLE_ADMIN`:
1. **`POST /concursos`**: Cria o concurso com período de inscrição e política de anulação (`ATRIBUIR_PONTO_A_TODOS` ou `REDISTRIBUIR_ENTRE_RESTANTES`).
2. **`POST /concursos/{id}/cargos`**: Cadastra os cargos e suas provas com respectivos pesos.
3. **`POST /provas/{id}/questoes`**: Cadastra o lote de questões da prova.
4. **`POST /provas/{id}/gabarito`**: Publica o gabarito oficial preliminar ou definitivo (dispara `GabaritoPublicado`).

### Passo 3: Inscrição, Boleto e Isenção (`ROLE_CANDIDATO` / `ROLE_ADMIN`)
1. **`POST /inscricoes`** (`ROLE_CANDIDATO`): Realiza a inscrição no cargo desejado (identidade extraída automaticamente do claim `sub` do JWT).
2. **`GET /inscricoes/{id}/boleto`** (`ROLE_CANDIDATO`): Emite/consulta a linha digitável e código de barras FEBRABAN (protegido contra IDOR — retorna `403 Forbidden` se outro candidato tentar acessar).
3. **`POST /inscricoes/{id}/isencao`** (`ROLE_CANDIDATO`): Solicita isenção de taxa dentro do prazo do edital.
4. **`PATCH /isencoes/{id}`** (`ROLE_ADMIN`): Deferir ou indeferir a isenção (dispara `IsencaoJulgada` e envia e-mail transacional verificável no Mailpit em `http://localhost:8025`).

### Passo 4: Logística de Locais, Alocação por Proximidade e Presença (`ROLE_ADMIN` / `ROLE_FISCAL`)
1. **`POST /locais-prova`** (`ROLE_ADMIN`): Cadastra o local de prova com salas (capacidade máxima e cargo atendido) e fiscais.
2. **`POST /concursos/{id}/alocacoes`** (`ROLE_ADMIN`): Distribui os candidatos homologados nas salas do mesmo cargo priorizando mesmo bairro/cidade e menor distância Haversine (dispara `SalaAlocada`).
3. **`POST /salas/{id}/presenca`** (`ROLE_FISCAL`): Registra a lista de presença dos candidatos alocados na sala.

### Passo 5: Importação Óptica CSV, Apuração e Desempenho (`ROLE_ADMIN` / `ROLE_CANDIDATO`)
1. **`POST /concursos/{id}/fichas-resposta`** (`ROLE_ADMIN`, `multipart/form-data`): Importa o arquivo CSV da leitora óptica tratando marcações válidas (`A`..`E`), em branco (` ` / `-`) e dupla marcação (`*`).
2. **`POST /concursos/{id}/apuracao`** (`ROLE_ADMIN`): Calcula as notas ponderadas, aplica as cláusulas de barreira (mínimo de 50% por prova e 60% na média ponderada) e gera a classificação (`NotasCalculadas`).
3. **`GET /concursos/{id}/classificados`** (Público): Lista os candidatos aprovados em ordem de classificação.
4. **`GET /concursos/{id}/meu-desempenho`** (`ROLE_CANDIDATO`): Retorna o boletim individual detalhado do candidato autenticado.

### Passo 6: Recursos, Recálculo Automático e Homologação Final
1. **`POST /recursos`** (`ROLE_CANDIDATO`): Protocola recurso fundamentado contra questão.
2. **`PATCH /recursos/{id}`** (`ROLE_ADMIN`): Julga o recurso. Se deferido com `"anularQuestao": true`, emite `QuestaoAnulada`, recalculando automaticamente as notas e reclassificando os candidatos do cargo (`ResultadoRepublicado`).
3. **`POST /concursos/{id}/resultado-final`** (`ROLE_ADMIN`): Valida que existem `0` recursos pendentes (retorna `422 Unprocessable Entity` caso contrário), encerra o concurso e publica o resultado final (`ResultadoFinalPublicado`).
