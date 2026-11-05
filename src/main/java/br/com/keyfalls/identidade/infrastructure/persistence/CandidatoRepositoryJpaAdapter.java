package br.com.keyfalls.identidade.infrastructure.persistence;

import br.com.keyfalls.identidade.domain.Candidato;
import br.com.keyfalls.identidade.domain.CandidatoRepository;
import br.com.keyfalls.identidade.domain.Credencial;
import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.security.PerfilAcesso;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class CandidatoRepositoryJpaAdapter implements CandidatoRepository {

    private final SpringDataCandidatoRepository springDataRepository;

    public CandidatoRepositoryJpaAdapter(SpringDataCandidatoRepository springDataRepository) {
        this.springDataRepository = Objects.requireNonNull(springDataRepository);
    }

    @Override
    public void salvar(Candidato candidato) {
        Endereco end = candidato.endereco();
        CandidatoJpaEntity entity = new CandidatoJpaEntity(
            candidato.id().valor(),
            candidato.cpf().numero(),
            candidato.nomeCompleto(),
            candidato.email().endereco(),
            candidato.credencial().senhaHash(),
            !candidato.credencial().bloqueada(),
            end.logradouro(),
            end.numero(),
            end.bairro(),
            end.cidade(),
            end.uf(),
            end.cep(),
            end.latitude(),
            end.longitude(),
            candidato.credencial().atualizadaEm(),
            Set.of(PerfilAcesso.ROLE_CANDIDATO.authority())
        );
        springDataRepository.save(entity);
    }

    @Override
    public Optional<Candidato> buscarPorId(CandidatoId id) {
        return springDataRepository.findById(id.valor()).map(this::toDomain);
    }

    @Override
    public Optional<Candidato> buscarPorCpf(CPF cpf) {
        return springDataRepository.findByCpf(cpf.numero()).map(this::toDomain);
    }

    @Override
    public Optional<Candidato> buscarPorEmail(Email email) {
        return springDataRepository.findByEmailIgnoreCase(email.endereco()).map(this::toDomain);
    }

    private Candidato toDomain(CandidatoJpaEntity entity) {
        return Candidato.reconstituir(
            new CandidatoId(entity.getId()),
            entity.getNomeCompleto(),
            new CPF(entity.getCpf()),
            new Email(entity.getEmail()),
            new Endereco(
                entity.getLogradouro(),
                entity.getNumero(),
                entity.getBairro(),
                entity.getCidade(),
                entity.getUf(),
                entity.getCep(),
                entity.getLatitude(),
                entity.getLongitude()
            ),
            new Credencial(entity.getSenhaHash(), !entity.isAtivo(), entity.getCriadoEm())
        );
    }
}
