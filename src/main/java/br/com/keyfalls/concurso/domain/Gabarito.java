package br.com.keyfalls.concurso.domain;

import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Gabarito {

    private final Map<QuestaoId, Alternativa> respostasOficiais;
    private boolean definitivo;

    Gabarito(Map<QuestaoId, Alternativa> respostasOficiais) {
        Objects.requireNonNull(respostasOficiais, "Mapa de respostas oficiais obrigatorio.");
        if (respostasOficiais.isEmpty()) {
            throw new RegraDeNegocioException("Gabarito nao pode ser publicado vazio.");
        }
        this.respostasOficiais = new LinkedHashMap<>(respostasOficiais);
        this.definitivo = false;
    }

    void retificarResposta(QuestaoId questaoId, Alternativa novaAlternativa) {
        if (definitivo) {
            throw new RegraDeNegocioException("Nao e permitido alterar resposta de gabarito ja consolidado como definitivo.");
        }
        Objects.requireNonNull(questaoId, "QuestaoId obrigatorio.");
        Objects.requireNonNull(novaAlternativa, "Nova alternativa obrigatoria.");
        if (!respostasOficiais.containsKey(questaoId)) {
            throw new RegraDeNegocioException("Questao nao pertence a este gabarito.");
        }
        respostasOficiais.put(questaoId, novaAlternativa);
    }

    void consolidarDefinitivo() {
        this.definitivo = true;
    }

    public Alternativa respostaCorretaPara(QuestaoId questaoId) {
        Alternativa resposta = respostasOficiais.get(questaoId);
        if (resposta == null) {
            throw new RegraDeNegocioException("Questao nao localizada no gabarito.");
        }
        return resposta;
    }

    public Map<QuestaoId, Alternativa> respostasOficiais() {
        return Collections.unmodifiableMap(respostasOficiais);
    }

    public boolean isDefinitivo() {
        return definitivo;
    }
}
