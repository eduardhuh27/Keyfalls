package br.com.keyfalls.shared.domain.specification;

import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.util.Objects;

/**
 * Contrato generico e composavel do padrao Specification (DDD) em Java 21.
 */
@FunctionalInterface
public interface Specification<T> {

    ResultadoAvaliacao avaliar(T candidato);

    record ResultadoAvaliacao(boolean satisfeito, String motivoFalha) {
        public static ResultadoAvaliacao ok() {
            return new ResultadoAvaliacao(true, null);
        }

        public static ResultadoAvaliacao falha(String motivo) {
            if (motivo == null || motivo.isBlank()) {
                throw new IllegalArgumentException("Motivo da falha na Specification obrigatorio.");
            }
            return new ResultadoAvaliacao(false, motivo.trim());
        }
    }

    default boolean isSatisfiedBy(T candidato) {
        return avaliar(candidato).satisfeito();
    }

    default void validarOuLancar(T candidato) {
        ResultadoAvaliacao resultado = avaliar(candidato);
        if (!resultado.satisfeito()) {
            throw new RegraDeNegocioException(resultado.motivoFalha());
        }
    }

    default Specification<T> and(Specification<T> outra) {
        Objects.requireNonNull(outra, "Specification combinada em AND nao pode ser nula.");
        return candidato -> {
            ResultadoAvaliacao primeiro = this.avaliar(candidato);
            return primeiro.satisfeito() ? outra.avaliar(candidato) : primeiro;
        };
    }

    default Specification<T> or(Specification<T> outra) {
        Objects.requireNonNull(outra, "Specification combinada em OR nao pode ser nula.");
        return candidato -> {
            ResultadoAvaliacao primeiro = this.avaliar(candidato);
            if (primeiro.satisfeito()) {
                return ResultadoAvaliacao.ok();
            }
            return outra.avaliar(candidato);
        };
    }
}
