package br.com.keyfalls;

import br.com.keyfalls.concurso.domain.Concurso;
import br.com.keyfalls.concurso.domain.Concurso.Abrangencia;
import br.com.keyfalls.inscricao.domain.Inscricao;
import br.com.keyfalls.inscricao.domain.StatusInscricao;
import br.com.keyfalls.logistica.domain.AlocacaoDeSala;
import br.com.keyfalls.logistica.domain.AlocadorDeSalas;
import br.com.keyfalls.logistica.domain.Fiscal;
import br.com.keyfalls.logistica.domain.LocalDeProva;
import br.com.keyfalls.logistica.domain.Sala;
import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.Percentual;
import br.com.keyfalls.shared.domain.Periodo;
import br.com.keyfalls.shared.domain.Peso;
import br.com.keyfalls.shared.domain.PoliticaAnulacaoQuestao;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.SalaId;
import br.com.keyfalls.shared.domain.exception.CapacidadeSalaExcedidaException;
import br.com.keyfalls.shared.domain.exception.CargoIncompativelNaSalaException;
import br.com.keyfalls.shared.domain.exception.ConflitoDeEstadoException;
import br.com.keyfalls.shared.domain.exception.PrazoIsencaoEncerradoException;
import br.com.keyfalls.shared.domain.exception.RecursosPendentesException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Suite Adversarial (Bug Hunter) para Invariantes de Agregados (Sala, LocalDeProva, AlocadorDeSalas,
 * Inscricao, Concurso) e auto-validacao de Value Objects.
 */
class RegrasDeAgregadoEdgeCasesTest {

    private static final CPF CPF_VALIDO_FISCAL = new CPF("52998224725");

    // =========================================================================
    // 1. SALA, LOCAL DE PROVA E ALOCADOR DE SALAS (CAPACIDADE N+1, CARGO E FALLBACK)
    // =========================================================================

    @Test
    @DisplayName("Deve bloquear o candidato N + 1 quando a Sala atingir capacidade maxima N")
    void deveLancarCapacidadeSalaExcedidaNoCandidatoNMaisUm() {
        CargoId cargoAnalista = CargoId.novo();
        LocalDeProva local = criarLocalPadrao("Centro", "Sao Paulo", "SP", "01001000");
        SalaId salaId = SalaId.novo();
        Sala sala = local.cadastrarSala(salaId, "Bloco A - Sala 101", 2, cargoAnalista, new Fiscal("Maria Fiscal", CPF_VALIDO_FISCAL));

        local.alocarCandidatoEmSalaEspecifica(salaId, InscricaoId.novo(), cargoAnalista);
        local.alocarCandidatoEmSalaEspecifica(salaId, InscricaoId.novo(), cargoAnalista);
        assertThat(sala.vagasDisponiveis()).isZero();

        // Tentativa de alocar o candidato N + 1 (3o candidato em sala de capacidade 2)
        assertThatThrownBy(() -> local.alocarCandidatoEmSalaEspecifica(salaId, InscricaoId.novo(), cargoAnalista))
            .isInstanceOf(CapacidadeSalaExcedidaException.class)
            .hasMessageContaining("Capacidade maxima (2) atingida");
    }

    @Test
    @DisplayName("Deve impedir alocacao de candidato do Cargo A em Sala exclusiva do Cargo B (Homogeneidade)")
    void deveBloquearAlocacaoDeCargoDiferenteNaSala() {
        CargoId cargoA = CargoId.novo();
        CargoId cargoB = CargoId.novo();
        LocalDeProva local = criarLocalPadrao("Bela Vista", "Sao Paulo", "SP", "01310100");
        SalaId salaExclusivaCargoB = SalaId.novo();
        local.cadastrarSala(salaExclusivaCargoB, "Sala 202", 30, cargoB, new Fiscal("Joao Fiscal", CPF_VALIDO_FISCAL));

        assertThatThrownBy(() -> local.alocarCandidatoEmSalaEspecifica(salaExclusivaCargoB, InscricaoId.novo(), cargoA))
            .isInstanceOf(CargoIncompativelNaSalaException.class)
            .hasMessageContaining("Violacao de homogeneidade de sala");
    }

    @Test
    @DisplayName("AlocadorDeSalas deve realizar fallback geografico quando bairro/cidade lotar e falhar se nao houver vaga global")
    void deveRealizarFallbackGeograficoOuLancarErroControladoQuandoCapacidadeEsgotar() {
        CargoId cargoAuditor = CargoId.novo();
        AlocadorDeSalas alocador = new AlocadorDeSalas();

        // Local 1 no mesmo bairro do candidato (capacidade = 1 vaga)
        LocalDeProva localMesmoBairro = criarLocalPadrao("Pinheiros", "Sao Paulo", "SP", "05422000");
        localMesmoBairro.cadastrarSala(SalaId.novo(), "Sala 01", 1, cargoAuditor, new Fiscal("Fiscal 1", CPF_VALIDO_FISCAL));

        // Local 2 em outra cidade/UF como contingencia (capacidade = 1 vaga)
        LocalDeProva localOutraCidade = criarLocalPadrao("Centro", "Campinas", "SP", "13010000");
        localOutraCidade.cadastrarSala(SalaId.novo(), "Sala Contingencia", 1, cargoAuditor, new Fiscal("Fiscal 2", CPF_VALIDO_FISCAL));

        Endereco endCandidatoPinheiros = new Endereco("Rua dos Pinheiros", "100", "Pinheiros", "Sao Paulo", "SP", "05422001");
        var cand1 = new AlocadorDeSalas.CandidatoHomologadoParaAlocacao(InscricaoId.novo(), cargoAuditor, endCandidatoPinheiros);
        var cand2SemVagaNoBairro = new AlocadorDeSalas.CandidatoHomologadoParaAlocacao(InscricaoId.novo(), cargoAuditor, endCandidatoPinheiros);
        var cand3ExcedenteGlobal = new AlocadorDeSalas.CandidatoHomologadoParaAlocacao(InscricaoId.novo(), cargoAuditor, endCandidatoPinheiros);

        List<AlocacaoDeSala> alocacoes = alocador.distribuirCandidatos(
            List.of(cand1, cand2SemVagaNoBairro),
            List.of(localOutraCidade, localMesmoBairro)
        );

        // Cand 1 pega Pinheiros (mesmo bairro); Cand 2 sofre fallback controlado para Campinas
        assertThat(alocacoes).hasSize(2);
        assertThat(alocacoes.get(0).localDeProvaId()).isEqualTo(localMesmoBairro.id());
        assertThat(alocacoes.get(1).localDeProvaId()).isEqualTo(localOutraCidade.id());

        // Cand 3 tenta alocar com ambos os locais lotados -> erro controlado de dominio
        assertThatThrownBy(() -> alocador.distribuirCandidatos(
            List.of(cand3ExcedenteGlobal),
            List.of(localOutraCidade, localMesmoBairro)
        ))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("Nao ha sala disponivel com capacidade para o cargo");
    }

    @Test
    @DisplayName("Colecao interna de inscricoes da Sala deve ser estritamente imutavel contra mutacao externa")
    void deveImpedirMutacaoDiretaDaListaInternaDaSala() {
        CargoId cargo = CargoId.novo();
        LocalDeProva local = criarLocalPadrao("Centro", "Curitiba", "PR", "80010000");
        Sala sala = local.cadastrarSala(SalaId.novo(), "Sala Blindada", 10, cargo, new Fiscal("Fiscal", CPF_VALIDO_FISCAL));

        assertThatThrownBy(() -> sala.inscricoesAlocadas().add(InscricaoId.novo()))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // =========================================================================
    // 2. INSCRICAO E ISENCAO: FRONTEIRA DE 1 SEGUNDO NO PERIODO (INSTANT BOUNDARY)
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}: instante={1} => deveRejeitar={2}")
    @CsvSource({
        "'1 segundo antes da abertura',   2026-11-10T07:59:59Z, true",
        "'Exatamente no milissegundo 0',  2026-11-10T08:00:00Z, true",
        "'1 segundo apos abertura (OK)',  2026-11-10T08:00:01Z, false",
        "'1 segundo antes do fim (OK)',   2026-11-12T23:59:58Z, false",
        "'Exatamente no fechamento',      2026-11-12T23:59:59Z, true",
        "'1 segundo apos o fechamento',   2026-11-13T00:00:00Z, true"
    })
    @DisplayName("Deve validar limites temporais de 1 segundo na solicitacao e no julgamento de isencao")
    void deveRespeitarFronteiraEstritaDeUmSegundoNoPeriodoDeIsencao(
        String cenario, String instanteIso, boolean deveRejeitar
    ) {
        Periodo janelaInscricao = new Periodo(
            Instant.parse("2026-11-01T00:00:00Z"),
            Instant.parse("2026-11-30T23:59:59Z")
        );
        Periodo janelaIsencao = new Periodo(
            Instant.parse("2026-11-10T08:00:00Z"),
            Instant.parse("2026-11-12T23:59:59Z")
        );
        Instant instanteTeste = Instant.parse(instanteIso);

        Inscricao inscricao = Inscricao.realizar(
            InscricaoId.novo(),
            new NumeroInscricao("2026000123"),
            CandidatoId.novo(),
            ConcursoId.novo(),
            CargoId.novo(),
            janelaInscricao,
            Instant.parse("2026-11-10T10:00:00Z")
        );

        if (deveRejeitar) {
            assertThatThrownBy(() -> inscricao.solicitarIsencao("Inscrito no CadUnico NIS ativo", janelaIsencao, instanteTeste))
                .as(cenario)
                .isInstanceOf(PrazoIsencaoEncerradoException.class);
        } else {
            inscricao.solicitarIsencao("Inscrito no CadUnico NIS ativo", janelaIsencao, instanteTeste);
            assertThat(inscricao.status()).isEqualTo(StatusInscricao.ISENCAO_EM_ANALISE);

            // Tambem testa o julgamento 1 segundo apos o encerramento do prazo -> deve bloquear!
            Instant umSegundoAposFechamento = janelaIsencao.fim().plusSeconds(1);
            assertThatThrownBy(() -> inscricao.deferirIsencao("Deferido fora do prazo", janelaIsencao, umSegundoAposFechamento))
                .isInstanceOf(PrazoIsencaoEncerradoException.class);
        }
    }

    @Test
    @DisplayName("Concurso deve bloquear homologacao com recursos pendentes e tornar-se estritamente imutavel apos homologado")
    void deveBloquearMutacoesAposHomologacaoDoConcurso() {
        Periodo inscricao = new Periodo(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-30T23:59:59Z"));
        Periodo isencao = new Periodo(Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-05T23:59:59Z"));

        Concurso concurso = Concurso.abrirEdital(
            ConcursoId.novo(), "EDITAL-01/2026", "Concurso Tribunal", Abrangencia.NACIONAL,
            inscricao, isencao, new PoliticaAnulacaoQuestao.PontoUniversalStrategy()
        );

        // 1. Bloqueia se houver recurso pendente
        assertThatThrownBy(() -> concurso.encerrarEHomologarResultadoFinal(true))
            .isInstanceOf(RecursosPendentesException.class);

        // 2. Homologa quando todos os recursos foram julgados
        concurso.encerrarEHomologarResultadoFinal(false);

        // 3. Impede qualquer alteracao normativa ou de gabarito apos homologado
        assertThatThrownBy(() -> concurso.adicionarCargo(CargoId.novo(), "Analista Judiciario", 10, new BigDecimal("120.00")))
            .isInstanceOf(ConflitoDeEstadoException.class)
            .hasMessageContaining("homologado");
    }

    // =========================================================================
    // 3. VALUE OBJECTS ADVERSARIAIS (CPF REPETIDO, PESO ZERO/NEGATIVO, PERCENTUAL, PERIODO)
    // =========================================================================

    @ParameterizedTest(name = "CPF invalido rejeitado: {0}")
    @ValueSource(strings = {
        "000.000.000-00",
        "111.111.111-11",
        "22222222222",
        "999.999.999-99",
        "52998224720",   // Digito verificador adulterado
        "1234567890",    // Apenas 10 digitos
        "abc.def.ghi-jk",
        "   "
    })
    @DisplayName("CPF deve rejeitar sequencias de digitos repetidos, DV invalido e comprimentos incorretos")
    void deveRejeitarCpfsInvalidosOuComDigitosRepetidos(String cpfInvalido) {
        assertThatThrownBy(() -> new CPF(cpfInvalido))
            .isInstanceOf(RegraDeNegocioException.class);
    }

    @ParameterizedTest(name = "Peso invalido rejeitado: {0}")
    @ValueSource(strings = {"0", "0.00", "-0.01", "-1.50", "-100"})
    @DisplayName("Peso deve rejeitar zero e valores negativos")
    void deveRejeitarPesosZeroOuNegativos(String pesoInvalido) {
        assertThatThrownBy(() -> Peso.de(pesoInvalido))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("maior que zero");
    }

    @ParameterizedTest(name = "Percentual fora de [0, 100] rejeitado: {0}")
    @ValueSource(strings = {"-0.01", "-50.00", "100.01", "100.5", "200.00"})
    @DisplayName("Percentual deve rejeitar valores menores que 0.00 ou maiores que 100.00")
    void deveRejeitarPercentuaisForaDoIntervaloValido(String valorInvalido) {
        assertThatThrownBy(() -> new Percentual(new BigDecimal(valorInvalido)))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("entre 0.00 e 100.00");
    }

    @Test
    @DisplayName("Periodo deve rejeitar data final anterior ou igual a data inicial")
    void deveRejeitarPeriodoInvertidoOuDeDuracaoZero() {
        Instant t1 = Instant.parse("2026-11-10T10:00:00Z");
        Instant t0 = Instant.parse("2026-11-10T09:59:59Z");

        assertThatThrownBy(() -> new Periodo(t1, t0))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("estritamente anterior ao fim");

        assertThatThrownBy(() -> new Periodo(t1, t1))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("estritamente anterior ao fim");
    }

    @Test
    @DisplayName("Email, Endereco e NumeroInscricao devem bloquear formatos malformados e coordenadas orfas")
    void deveRejeitarEmailsEnderecosENumerosInscricaoMalformados() {
        assertThatThrownBy(() -> new Email("candidato-sem-arroba.com.br"))
            .isInstanceOf(RegraDeNegocioException.class);

        // Latitude informada sem longitude correspondente
        assertThatThrownBy(() -> new Endereco("Av Paulista", "1000", "Bela Vista", "Sao Paulo", "SP", "01310100", -23.56, null))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("em conjunto");

        // Coordenada fora do globo terrestre (-95 graus de latitude)
        assertThatThrownBy(() -> new Endereco("Av Paulista", "1000", "Bela Vista", "Sao Paulo", "SP", "01310100", -95.0, -46.65))
            .isInstanceOf(RegraDeNegocioException.class)
            .hasMessageContaining("limites validos");

        assertThatThrownBy(() -> new NumeroInscricao("26-123"))
            .isInstanceOf(RegraDeNegocioException.class);
    }

    private static LocalDeProva criarLocalPadrao(String bairro, String cidade, String uf, String cep) {
        return LocalDeProva.credenciar(
            LocalDeProvaId.novo(),
            ConcursoId.novo(),
            "Universidade KeyFalls - Campus " + bairro,
            new Endereco("Rua Principal", "500", bairro, cidade, uf, cep)
        );
    }
}
