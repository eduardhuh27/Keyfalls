package br.com.keyfalls.inscricao.application;

import br.com.keyfalls.inscricao.domain.Boleto;
import br.com.keyfalls.inscricao.domain.Inscricao;
import br.com.keyfalls.inscricao.domain.InscricaoRepository;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/**
 * Use Case puro de aplicacao (livre de @Service / @Transactional do Spring).
 * Instanciado e envelopado transacionalmente via @Configuration na camada infrastructure.
 */
public class RealizarInscricaoUseCase {

    private final InscricaoRepository inscricaoRepository;
    private final ConsultaOfertaConcursoPort consultaOfertaConcursoPort;
    private final GeradorNumeroInscricaoPort geradorNumeroInscricaoPort;
    private final EmissorBoletoBancarioPort emissorBoletoBancarioPort;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RealizarInscricaoUseCase(
        InscricaoRepository inscricaoRepository,
        ConsultaOfertaConcursoPort consultaOfertaConcursoPort,
        GeradorNumeroInscricaoPort geradorNumeroInscricaoPort,
        EmissorBoletoBancarioPort emissorBoletoBancarioPort,
        DomainEventPublisher eventPublisher,
        Clock clock
    ) {
        this.inscricaoRepository = Objects.requireNonNull(inscricaoRepository);
        this.consultaOfertaConcursoPort = Objects.requireNonNull(consultaOfertaConcursoPort);
        this.geradorNumeroInscricaoPort = Objects.requireNonNull(geradorNumeroInscricaoPort);
        this.emissorBoletoBancarioPort = Objects.requireNonNull(emissorBoletoBancarioPort);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public InscricaoRealizadaOutput executar(RealizarInscricaoCommand command) {
        Objects.requireNonNull(command, "Comando RealizarInscricaoCommand obrigatorio.");

        CandidatoId candidatoId = new CandidatoId(command.candidatoId());
        ConcursoId concursoId = new ConcursoId(command.concursoId());
        CargoId cargoId = new CargoId(command.cargoId());

        if (inscricaoRepository.existeInscricaoAtiva(candidatoId, concursoId)) {
            throw new RegraDeNegocioException("O candidato ja possui uma inscricao ativa para este concurso.");
        }

        var oferta = consultaOfertaConcursoPort.consultarOferta(concursoId, cargoId)
            .orElseThrow(() -> new RegraDeNegocioException("Cargo ou concurso nao disponivel para inscricao."));

        NumeroInscricao numeroGerado = geradorNumeroInscricaoPort.proximoNumero(concursoId);
        Instant agora = Instant.now(clock);

        Inscricao inscricao = Inscricao.realizar(
            InscricaoId.novo(),
            numeroGerado,
            candidatoId,
            concursoId,
            cargoId,
            oferta.periodoInscricao(),
            agora
        );

        String linhaDigitavel = emissorBoletoBancarioPort.registrarCobrancaBancaria(
            numeroGerado,
            oferta.taxaInscricao(),
            oferta.vencimentoBoleto()
        );
        Boleto boleto = inscricao.gerarBoleto(linhaDigitavel, oferta.taxaInscricao(), oferta.vencimentoBoleto());

        inscricaoRepository.salvar(inscricao);
        eventPublisher.publishAll(inscricao.pullDomainEvents());

        return new InscricaoRealizadaOutput(
            inscricao.id().valor(),
            inscricao.numero().codigo(),
            inscricao.status().name(),
            boleto.linhaDigitavel(),
            boleto.valor(),
            boleto.vencimento()
        );
    }
}
