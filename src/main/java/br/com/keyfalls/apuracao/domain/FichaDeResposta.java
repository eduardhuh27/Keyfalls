package br.com.keyfalls.apuracao.domain;

import br.com.keyfalls.shared.domain.AggregateRoot;
import br.com.keyfalls.shared.domain.Alternativa;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.FichaDeRespostaId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.ProvaId;
import br.com.keyfalls.shared.domain.QuestaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FichaDeResposta extends AggregateRoot<FichaDeRespostaId> {

    private final FichaDeRespostaId id;
    private final InscricaoId inscricaoId;
    private final ConcursoId concursoId;
    private final Instant capturadaEm;
    private final Map<ProvaId, Map<QuestaoId, Alternativa>> respostasPorProva;

    private FichaDeResposta(
        FichaDeRespostaId id,
        InscricaoId inscricaoId,
        ConcursoId concursoId,
        Instant capturadaEm,
        Map<ProvaId, Map<QuestaoId, Alternativa>> respostasPorProva
    ) {
        this.id = Objects.requireNonNull(id, "FichaDeRespostaId obrigatorio.");
        this.inscricaoId = Objects.requireNonNull(inscricaoId, "InscricaoId obrigatorio.");
        this.concursoId = Objects.requireNonNull(concursoId, "ConcursoId obrigatorio.");
        this.capturadaEm = Objects.requireNonNull(capturadaEm, "Data/hora de captura obrigatoria.");
        Objects.requireNonNull(respostasPorProva, "Mapa de respostas por prova obrigatorio.");
        if (respostasPorProva.isEmpty()) {
            throw new RegraDeNegocioException("A ficha de resposta deve conter marcacoes de ao menos uma prova.");
        }

        Map<ProvaId, Map<QuestaoId, Alternativa>> copiaExterna = new LinkedHashMap<>();
        respostasPorProva.forEach((provaId, marcacoes) -> {
            Objects.requireNonNull(provaId, "ProvaId obrigatorio na ficha de resposta.");
            Objects.requireNonNull(marcacoes, "Mapa de marcacoes da prova obrigatorio.");
            Map<QuestaoId, Alternativa> copiaInterna = new LinkedHashMap<>();
            marcacoes.forEach((questaoId, alternativa) -> {
                Objects.requireNonNull(questaoId, "QuestaoId obrigatorio na marcacao.");
                // alternativa nula representa questao deixada em branco ou dupla marcacao invalidada na leitura optica
                copiaInterna.put(questaoId, alternativa);
            });
            copiaExterna.put(provaId, Collections.unmodifiableMap(copiaInterna));
        });
        this.respostasPorProva = Collections.unmodifiableMap(copiaExterna);
    }

    public static FichaDeResposta capturar(
        FichaDeRespostaId id,
        InscricaoId inscricaoId,
        ConcursoId concursoId,
        Instant capturadaEm,
        Map<ProvaId, Map<QuestaoId, Alternativa>> respostasPorProva
    ) {
        return new FichaDeResposta(id, inscricaoId, concursoId, capturadaEm, respostasPorProva);
    }

    public Optional<Alternativa> respostaMarcada(ProvaId provaId, QuestaoId questaoId) {
        Map<QuestaoId, Alternativa> respostasDaProva = respostasPorProva.get(provaId);
        if (respostasDaProva == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(respostasDaProva.get(questaoId));
    }

    public boolean contemProva(ProvaId provaId) {
        return respostasPorProva.containsKey(provaId);
    }

    @Override
    public FichaDeRespostaId id() {
        return id;
    }

    public InscricaoId inscricaoId() {
        return inscricaoId;
    }

    public ConcursoId concursoId() {
        return concursoId;
    }

    public Instant capturadaEm() {
        return capturadaEm;
    }

    public Map<ProvaId, Map<QuestaoId, Alternativa>> respostasPorProva() {
        return respostasPorProva;
    }
}
