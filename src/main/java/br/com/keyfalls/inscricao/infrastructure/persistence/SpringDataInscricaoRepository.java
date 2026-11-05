package br.com.keyfalls.inscricao.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataInscricaoRepository extends JpaRepository<InscricaoJpaEntity, UUID> {

    boolean existsByCandidatoIdAndConcursoIdAndStatusNot(UUID candidatoId, UUID concursoId, String statusExcluido);

    List<InscricaoJpaEntity> findByConcursoIdAndStatus(UUID concursoId, String status);
}
