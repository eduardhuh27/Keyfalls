package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.AggregateRoot;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.Percentual;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.ResultadoId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Resultado extends AggregateRoot<ResultadoId> {

    private final ResultadoId id;
    private final InscricaoId inscricaoId;
    private final ConcursoId concursoId;
    private final CargoId cargoId;
    private final List<NotaProva> notasPorProva;
    private final BigDecimal notaTotalPonderada;
    private final Percentual percentualTotalPonderado;
    private final int totalAcertos;
    private final int totalErros;
    private final SituacaoResultado situacao;

    private Resultado(
        ResultadoId id,
        InscricaoId inscricaoId,
        ConcursoId concursoId,
        CargoId cargoId,
        List<NotaProva> notasPorProva,
        BigDecimal notaTotalPonderada,
        Percentual percentualTotalPonderado,
        int totalAcertos,
        int totalErros,
        SituacaoResultado situacao
    ) {
        this.id = Objects.requireNonNull(id, "ResultadoId obrigatorio.");
        this.inscricaoId = Objects.requireNonNull(inscricaoId, "InscricaoId obrigatorio.");
        this.concursoId = Objects.requireNonNull(concursoId, "ConcursoId obrigatorio.");
        this.cargoId = Objects.requireNonNull(cargoId, "CargoId obrigatorio.");
        this.notasPorProva = Collections.unmodifiableList(List.copyOf(notasPorProva));
        this.notaTotalPonderada = notaTotalPonderada;
        this.percentualTotalPonderado = percentualTotalPonderado;
        this.totalAcertos = totalAcertos;
        this.totalErros = totalErros;
        this.situacao = situacao;
    }

    /**
     * Regra de Negocio (Aprovacao e Corte):
     * Minimo de 50% de acertos em cada prova individualmente
     * E minimo de 60% no total sobre a nota ponderada.
     */
    public static Resultado consolidar(
        ResultadoId id,
        InscricaoId inscricaoId,
        ConcursoId concursoId,
        CargoId cargoId,
        List<NotaProva> notasPorProva
    ) {
        Objects.requireNonNull(notasPorProva, "Lista de notas por prova obrigatoria.");
        if (notasPorProva.isEmpty()) {
            throw new RegraDeNegocioException("O resultado deve consolidar ao menos uma prova.");
        }

        BigDecimal somaNotasVezesPeso = BigDecimal.ZERO;
        BigDecimal somaPesos = BigDecimal.ZERO;
        int somaAcertos = 0;
        int somaErros = 0;
        boolean aprovadoEmTodasAsProvas = true;

        for (NotaProva nota : notasPorProva) {
            Objects.requireNonNull(nota, "NotaProva nao pode ser nula.");
            somaNotasVezesPeso = somaNotasVezesPeso.add(
                nota.notaExataParaPonderacao().multiply(nota.peso().valor())
            );
            somaPesos = somaPesos.add(nota.peso().valor());
            somaAcertos += nota.acertos();
            somaErros += nota.erros();

            if (!nota.atingiuMinimoIndividual()) {
                aprovadoEmTodasAsProvas = false;
            }
        }

        // Como cada nota individual esta na escala 0..100, a media ponderada pelos pesos
        // expressa diretamente a nota total ponderada (0.00 a 100.00) e seu respectivo percentual.
        BigDecimal mediaPonderada = somaNotasVezesPeso.divide(somaPesos, 2, RoundingMode.HALF_UP);
        Percentual percentualPonderado = new Percentual(mediaPonderada);

        boolean atingiuCorteGlobal = percentualPonderado.atingiuMinimo(Percentual.SESSENTA_POR_CENTO);
        SituacaoResultado situacaoCalculada = (aprovadoEmTodasAsProvas && atingiuCorteGlobal)
            ? SituacaoResultado.CLASSIFICADO
            : SituacaoResultado.DESCLASSIFICADO;

        return new Resultado(
            id,
            inscricaoId,
            concursoId,
            cargoId,
            notasPorProva,
            mediaPonderada,
            percentualPonderado,
            somaAcertos,
            somaErros,
            situacaoCalculada
        );
    }

    public boolean estaClassificado() {
        return this.situacao == SituacaoResultado.CLASSIFICADO;
    }

    @Override
    public ResultadoId id() {
        return id;
    }

    public InscricaoId inscricaoId() {
        return inscricaoId;
    }

    public ConcursoId concursoId() {
        return concursoId;
    }

    public CargoId cargoId() {
        return cargoId;
    }

    public List<NotaProva> notasPorProva() {
        return notasPorProva;
    }

    public BigDecimal notaTotalPonderada() {
        return notaTotalPonderada;
    }

    public Percentual percentualTotalPonderado() {
        return percentualTotalPonderado;
    }

    public int totalAcertos() {
        return totalAcertos;
    }

    public int totalErros() {
        return totalErros;
    }

    public SituacaoResultado situacao() {
        return situacao;
    }
}
