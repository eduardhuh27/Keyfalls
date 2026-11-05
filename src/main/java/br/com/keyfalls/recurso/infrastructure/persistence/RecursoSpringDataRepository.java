package br.com.keyfalls.recurso.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RecursoSpringDataRepository extends JpaRepository<RecursoJpaEntity, UUID> {

    boolean existsByConcursoIdAndDecisaoIsNull(UUID concursoId);
}
