package br.com.keyfalls.concurso.domain;

import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

public final class Questao {

    private final QuestaoId id;
    private final int numeroOrdem;
    private final String enunciado;
    private final Map<Alternativa, String> opcoes;
    private boolean anulada;
    private String justificativaAnulacao;

    Questao(QuestaoId id, int numeroOrdem, String enunciado, Map<Alternativa, String> opcoes) {
        this.id = Objects.requireNonNull(id, "QuestaoId obrigatorio.");
        if (numeroOrdem <= 0) {
            throw new RegraDeNegocioException("Numero de ordem da questao deve ser positivo.");
        }
        if (enunciado == null || enunciado.isBlank()) {
            throw new RegraDeNegocioException("Enunciado da questao obrigatorio.");
        }
        Objects.requireNonNull(opcoes, "Opcoes da questao obrigatorias.");
        if (opcoes.size() < 2) {
            throw new RegraDeNegocioException("A questao deve possuir ao menos 2 alternativas.");
        }
        Map<Alternativa, String> copia = new EnumMap<>(Alternativa.class);
        opcoes.forEach((letra, texto) -> {
            Objects.requireNonNull(letra, "Letra da alternativa obrigatoria.");
            if (texto == null || texto.isBlank()) {
                throw new RegraDeNegocioException("Texto da alternativa " + letra + " nao pode ser vazio.");
            }
            copia.put(letra, texto.trim());
        });

        this.numeroOrdem = numeroOrdem;
        this.enunciado = enunciado.trim();
        this.opcoes = Collections.unmodifiableMap(copia);
        this.anulada = false;
    }

    void anular(String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new RegraDeNegocioException("Justificativa de anulacao da questao obrigatoria.");
        }
        if (this.anulada) {
            throw new RegraDeNegocioException("A questao " + numeroOrdem + " ja encontra-se anulada.");
        }
        this.anulada = true;
        this.justificativaAnulacao = justificativa.trim();
    }

    public boolean possuiAlternativa(Alternativa alternativa) {
        return opcoes.containsKey(alternativa);
    }

    public QuestaoId id() {
        return id;
    }

    public int numeroOrdem() {
        return numeroOrdem;
    }

    public String enunciado() {
        return enunciado;
    }

    public Map<Alternativa, String> opcoes() {
        return opcoes;
    }

    public boolean isAnulada() {
        return anulada;
    }

    public String justificativaAnulacao() {
        return justificativaAnulacao;
    }
}
