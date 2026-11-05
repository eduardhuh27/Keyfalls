package br.com.keyfalls.identidade.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataCandidatoRepository extends JpaRepository<CandidatoJpaEntity, UUID> {

    Optional<CandidatoJpaEntity> findByCpf(String cpf);

    Optional<CandidatoJpaEntity> findByEmailIgnoreCase(String email);
}
