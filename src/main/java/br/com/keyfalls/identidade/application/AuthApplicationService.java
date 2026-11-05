package br.com.keyfalls.identidade.application;

import br.com.keyfalls.identidade.domain.Candidato;
import br.com.keyfalls.identidade.domain.CandidatoRepository;
import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.exception.ConflitoDeEstadoException;
import br.com.keyfalls.shared.domain.security.PerfilAcesso;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implementacao pura de aplicacao dos casos de uso de Identidade & Autenticacao JWT.
 */
public class AuthApplicationService implements AuthUseCases {

    private final CandidatoRepository candidatoRepository;
    private final HashSenhaPort hashSenhaPort;
    private final EmissorTokenJwtPort emissorTokenJwtPort;
    private final Clock clock;

    public AuthApplicationService(
        CandidatoRepository candidatoRepository,
        HashSenhaPort hashSenhaPort,
        EmissorTokenJwtPort emissorTokenJwtPort,
        Clock clock
    ) {
        this.candidatoRepository = Objects.requireNonNull(candidatoRepository);
        this.hashSenhaPort = Objects.requireNonNull(hashSenhaPort);
        this.emissorTokenJwtPort = Objects.requireNonNull(emissorTokenJwtPort);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public CandidatoRegistradoOutput registrarCandidato(RegistrarCandidatoCommand command) {
        Objects.requireNonNull(command, "RegistrarCandidatoCommand obrigatorio.");
        if (command.senhaPura() == null || command.senhaPura().length() < 8) {
            throw new RegraDeNegocioException("A senha deve conter no minimo 8 caracteres.");
        }

        CPF cpf = new CPF(command.cpf());
        Email email = new Email(command.email());

        if (candidatoRepository.buscarPorCpf(cpf).isPresent()) {
            throw new ConflitoDeEstadoException("Ja existe candidato cadastrado com o CPF informado.");
        }
        if (candidatoRepository.buscarPorEmail(email).isPresent()) {
            throw new ConflitoDeEstadoException("Ja existe candidato cadastrado com o e-mail informado.");
        }

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

        Instant agora = Instant.now(clock);
        String senhaHash = hashSenhaPort.gerarHash(command.senhaPura());

        Candidato candidato = Candidato.cadastrar(
            CandidatoId.novo(),
            command.nomeCompleto(),
            cpf,
            email,
            endereco,
            senhaHash,
            agora
        );

        candidatoRepository.salvar(candidato);
        eventPublisherNoOpOrDrain(candidato);

        String token = emissorTokenJwtPort.emitirToken(
            candidato.id().valor(),
            candidato.email().endereco(),
            Set.of(PerfilAcesso.ROLE_CANDIDATO)
        );

        return new CandidatoRegistradoOutput(
            candidato.id().valor(),
            candidato.nomeCompleto(),
            candidato.cpf().formatado(),
            candidato.email().endereco(),
            token
        );
    }

    @Override
    public TokenAutenticacaoOutput autenticar(LoginCommand command) {
        Objects.requireNonNull(command, "LoginCommand obrigatorio.");
        if (command.loginCpfOuEmail() == null || command.loginCpfOuEmail().isBlank()
            || command.senhaPura() == null || command.senhaPura().isBlank()) {
            throw new RegraDeNegocioException("Credenciais de login obrigatorias.");
        }

        String login = command.loginCpfOuEmail().trim();
        Optional<Candidato> localizado = login.contains("@")
            ? candidatoRepository.buscarPorEmail(new Email(login))
            : candidatoRepository.buscarPorCpf(new CPF(login));

        Candidato candidato = localizado
            .orElseThrow(() -> new RegraDeNegocioException("Credenciais invalidas ou usuario nao encontrado."));

        if (candidato.credencial().bloqueada()) {
            throw new RegraDeNegocioException("Conta bloqueada por seguranca.");
        }

        if (!hashSenhaPort.verificar(command.senhaPura(), candidato.credencial().senhaHash())) {
            throw new RegraDeNegocioException("Credenciais invalidas ou usuario nao encontrado.");
        }

        Set<PerfilAcesso> perfis = Set.of(PerfilAcesso.ROLE_CANDIDATO);
        String token = emissorTokenJwtPort.emitirToken(
            candidato.id().valor(),
            candidato.email().endereco(),
            perfis
        );

        return new TokenAutenticacaoOutput(
            candidato.id().valor(),
            candidato.email().endereco(),
            Set.of(PerfilAcesso.ROLE_CANDIDATO.authority()),
            token
        );
    }

    @Override
    public TokenAutenticacaoOutput emitirTokenPorPerfil(EmitirTokenPerfilCommand command) {
        Objects.requireNonNull(command, "EmitirTokenPerfilCommand obrigatorio.");
        PerfilAcesso perfil = command.perfil() != null ? command.perfil() : PerfilAcesso.ROLE_ADMIN;
        String email = (command.email() != null && !command.email().isBlank())
            ? command.email().trim()
            : "admin@keyfalls.com.br";
        UUID id = UUID.randomUUID();
        String token = emissorTokenJwtPort.emitirToken(id, email, Set.of(perfil));
        return new TokenAutenticacaoOutput(id, email, Set.of(perfil.authority()), token);
    }

    private static void eventPublisherNoOpOrDrain(Candidato candidato) {
        candidato.pullDomainEvents();
    }
}
