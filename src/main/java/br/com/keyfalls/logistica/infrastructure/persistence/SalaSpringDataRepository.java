package br.com.keyfalls.logistica.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SalaSpringDataRepository extends JpaRepository<SalaJpaEntity, UUID> {
}
