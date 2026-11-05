package br.com.keyfalls.inscricao.application;

import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.Periodo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Porta secundaria (Outbound ACL Port) para consultar condicoes de oferta do cargo no concurso
 * sem importar entidades de br.com.keyfalls.concurso.domain.
 */
public interface ConsultaOfertaConcursoPort {

    record DadosOfertaCargo(
        Periodo periodoInscricao,
        Periodo periodoIsencao,
        BigDecimal taxaInscricao,
        LocalDate vencimentoBoleto
    ) {
        public DadosOfertaCargo(Periodo periodoInscricao, BigDecimal taxaInscricao, LocalDate vencimentoBoleto) {
            this(periodoInscricao, periodoInscricao, taxaInscricao, vencimentoBoleto);
        }
    }

    Optional<DadosOfertaCargo> consultarOferta(ConcursoId concursoId, CargoId cargoId);
}
