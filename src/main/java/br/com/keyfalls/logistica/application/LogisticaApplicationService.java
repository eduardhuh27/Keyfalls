package br.com.keyfalls.logistica.application;

import br.com.keyfalls.logistica.domain.AlocacaoDeSala;
import br.com.keyfalls.logistica.domain.AlocacaoDeSalaRepository;
import br.com.keyfalls.logistica.domain.AlocadorDeSalas;
import br.com.keyfalls.logistica.domain.AlocadorDeSalas.CandidatoHomologadoParaAlocacao;
import br.com.keyfalls.logistica.domain.Fiscal;
import br.com.keyfalls.logistica.domain.LocalDeProva;
import br.com.keyfalls.logistica.domain.LocalDeProvaRepository;
import br.com.keyfalls.logistica.domain.Sala;
import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.LocalDeProvaId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.SalaId;
import br.com.keyfalls.shared.domain.event.DomainEventPublisher;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Implementacao concreta dos Use Cases do Bounded Context Logistica.
 * Orquestra o Domain Service AlocadorDeSalas, persiste os agregados LocalDeProva e AlocacaoDeSala,
 * e drena os eventos de dominio (SalaAlocada) via pullDomainEvents().
 */
public class LogisticaApplicationService implements LogisticaUseCases {

    private final LocalDeProvaRepository localDeProvaRepository;
    private final AlocacaoDeSalaRepository alocacaoDeSalaRepository;
    private final ConsultaCandidatosHomologadosPort consultaCandidatosHomologadosPort;
    private final AlocadorDeSalas alocadorDeSalas;
    private final DomainEventPublisher eventPublisher;

    public LogisticaApplicationService(
        LocalDeProvaRepository localDeProvaRepository,
        AlocacaoDeSalaRepository alocacaoDeSalaRepository,
        ConsultaCandidatosHomologadosPort consultaCandidatosHomologadosPort,
        AlocadorDeSalas alocadorDeSalas,
        DomainEventPublisher eventPublisher
    ) {
        this.localDeProvaRepository = Objects.requireNonNull(localDeProvaRepository);
        this.alocacaoDeSalaRepository = Objects.requireNonNull(alocacaoDeSalaRepository);
        this.consultaCandidatosHomologadosPort = Objects.requireNonNull(consultaCandidatosHomologadosPort);
        this.alocadorDeSalas = Objects.requireNonNull(alocadorDeSalas);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public LocalDeProvaCriadoOutput cadastrarLocalSalasEFiscais(CadastrarLocalDeProvaCommand command) {
        Objects.requireNonNull(command, "Comando de cadastro de local de prova obrigatorio.");

        Endereco endereco = new Endereco(
            command.logradouro(),
            command.numero(),
            command.bairro(),
            command.cidade(),
            command.uf(),
            command.cep(),
            command.latitude(),
            command.longitude()
        );

        LocalDeProva local = LocalDeProva.credenciar(
            LocalDeProvaId.novo(),
            new ConcursoId(command.concursoId()),
            command.nomeInstituicao(),
            endereco
        );

        List<UUID> salasIds = new ArrayList<>();
        for (SalaCadastroInput s : command.salas()) {
            Sala sala = local.cadastrarSala(
                SalaId.novo(),
                s.identificacao(),
                s.capacidadeMaxima(),
                new CargoId(s.cargoAtendidoId()),
                new Fiscal(s.nomeFiscal(), new CPF(s.cpfFiscal()))
            );
            salasIds.add(sala.id().valor());
        }

        localDeProvaRepository.salvar(local);
        local.pullDomainEvents().forEach(eventPublisher::publish);

        return new LocalDeProvaCriadoOutput(
            local.id().valor(),
            local.nomeInstituicao(),
            List.copyOf(salasIds)
        );
    }

    @Override
    public AlocacaoEmLoteOutput executarAlocacaoPorProximidade(UUID concursoId) {
        Objects.requireNonNull(concursoId, "ConcursoId obrigatorio para alocacao em lote.");
        ConcursoId idConcurso = new ConcursoId(concursoId);

        List<LocalDeProva> locaisDisponiveis = localDeProvaRepository.listarPorConcurso(idConcurso);
        if (locaisDisponiveis.isEmpty()) {
            throw new RegraDeNegocioException("Nenhum local de prova credenciado para o concurso " + concursoId);
        }

        List<CandidatoHomologadoParaAlocacao> candidatos =
            consultaCandidatosHomologadosPort.listarHomologadosPendentesDeAlocacao(idConcurso);

        List<AlocacaoDeSala> alocacoes = alocadorDeSalas.distribuirCandidatos(candidatos, locaisDisponiveis);

        for (LocalDeProva local : locaisDisponiveis) {
            localDeProvaRepository.salvar(local);
            local.pullDomainEvents().forEach(eventPublisher::publish);
        }

        alocacaoDeSalaRepository.salvarTodas(alocacoes);
        for (AlocacaoDeSala alocacao : alocacoes) {
            alocacao.pullDomainEvents().forEach(eventPublisher::publish);
        }

        return new AlocacaoEmLoteOutput(concursoId, alocacoes.size(), Instant.now());
    }

    @Override
    public ListaPresencaRegistradaOutput registrarListaDePresenca(RegistrarListaPresencaCommand command) {
        Objects.requireNonNull(command, "Comando de registro de presenca obrigatorio.");
        SalaId salaEsperada = new SalaId(command.salaId());
        Instant horarioRegistro = Instant.now();

        int presentes = 0;
        int ausentes = 0;

        for (RegistroPresencaItemInput item : command.registros()) {
            AlocacaoDeSala alocacao = alocacaoDeSalaRepository
                .buscarPorInscricao(new InscricaoId(item.inscricaoId()))
                .orElseThrow(() -> new RegraDeNegocioException(
                    "Alocacao de sala nao encontrada para a inscricao: " + item.inscricaoId()
                ));

            if (!alocacao.salaId().equals(salaEsperada)) {
                throw new RegraDeNegocioException(
                    "Violacao de sala: a inscricao %s nao pertence a sala %s."
                        .formatted(item.inscricaoId(), command.salaId())
                );
            }

            if (item.presente()) {
                alocacao.registrarPresencaAssinada(
                    item.hashAssinaturaCandidato(),
                    item.documentoApresentado(),
                    horarioRegistro
                );
                presentes++;
            } else {
                alocacao.registrarAusencia(horarioRegistro);
                ausentes++;
            }

            alocacaoDeSalaRepository.salvar(alocacao);
            alocacao.pullDomainEvents().forEach(eventPublisher::publish);
        }

        return new ListaPresencaRegistradaOutput(command.salaId(), presentes, ausentes, horarioRegistro);
    }
}
