package br.com.keyfalls.inscricao.infrastructure.persistence;

import br.com.keyfalls.inscricao.domain.Inscricao;
import br.com.keyfalls.inscricao.domain.InscricaoRepository;
import br.com.keyfalls.inscricao.domain.StatusInscricao;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class InscricaoRepositoryAdapter implements InscricaoRepository {

    private final SpringDataInscricaoRepository springDataRepository;
    private final InscricaoPersistenceMapper mapper;

    public InscricaoRepositoryAdapter(
        SpringDataInscricaoRepository springDataRepository,
        InscricaoPersistenceMapper mapper
    ) {
        this.springDataRepository = Objects.requireNonNull(springDataRepository);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void salvar(Inscricao inscricao) {
        InscricaoJpaEntity entity = mapper.toJpaEntity(inscricao);
        springDataRepository.save(entity);
    }

    @Override
    public Optional<Inscricao> buscarPorId(InscricaoId id) {
        return springDataRepository.findById(id.valor()).map(mapper::toDomain);
    }

    @Override
    public boolean existeInscricaoAtiva(CandidatoId candidatoId, ConcursoId concursoId) {
        return springDataRepository.existsByCandidatoIdAndConcursoIdAndStatusNot(
            candidatoId.valor(),
            concursoId.valor(),
            StatusInscricao.CANCELADA.name()
        );
    }

    @Override
    public List<Inscricao> listarHomologadasPorConcurso(ConcursoId concursoId) {
        return springDataRepository
            .findByConcursoIdAndStatus(concursoId.valor(), StatusInscricao.HOMOLOGADA.name())
            .stream()
            .map(mapper::toDomain)
            .toList();
    }
}
