package br.com.keyfalls.recurso.domain;

import br.com.keyfalls.shared.domain.ConfiguracaoRegrasEdital;
import br.com.keyfalls.shared.domain.specification.Specification;
import br.com.keyfalls.shared.domain.specification.Specification.ResultadoAvaliacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Specification composavel para validar abertura de recursos conforme os limites
 * e prazos do edital, alem de gerar o hash de similaridade textual para agrupamento
 * de recursos identicos ("copia e cola") em lote pela banca examinadora.
 */
public final class ValidadorAberturaRecurso {

    public record ContextoAberturaRecurso(
        Instant gabaritoPreliminarPublicadoEm,
        Instant protocoladoEm,
        boolean candidatoJaRecorreuDestaQuestao,
        int quantidadeRecursosDoCandidatoNoConcurso,
        String fundamentacao,
        ConfiguracaoRegrasEdital regrasEdital
    ) {
        public ContextoAberturaRecurso {
            Objects.requireNonNull(gabaritoPreliminarPublicadoEm, "Data de publicacao do gabarito obrigatoria.");
            Objects.requireNonNull(protocoladoEm, "Data de protocolo do recurso obrigatoria.");
            Objects.requireNonNull(regrasEdital, "Regras do edital obrigatorias.");
        }
    }

    private ValidadorAberturaRecurso() {}

    public static Specification<ContextoAberturaRecurso> construirEspecificacao() {
        return tempestividadeJanelaRecursalSpec()
            .and(unicidadePorQuestaoECandidatoSpec())
            .and(tetoGlobalRecursosPorCandidatoSpec())
            .and(fundamentacaoConsistenteSpec());
    }

    public static void validarOuRejeitar(ContextoAberturaRecurso contexto) {
        construirEspecificacao().validarOuLancar(contexto);
    }

    /**
     * Gera assinatura SHA-256 sobre o texto normalizado (sem acentos, pontuacao ou espacos extras),
     * permitindo agrupar recursos identicos em lote no painel da banca examinadora.
     */
    public static String gerarHashAgrupamentoArgumentacao(String fundamentacao) {
        String semAcentos = Normalizer.normalize(fundamentacao, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        String canonico = semAcentos.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonico.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponivel no runtime Java.", e);
        }
    }

    private static Specification<ContextoAberturaRecurso> tempestividadeJanelaRecursalSpec() {
        return ctx -> {
            Instant limite = ctx.gabaritoPreliminarPublicadoEm().plus(ctx.regrasEdital().prazoAberturaRecursoAposGabarito());
            boolean tempestivo = !ctx.protocoladoEm().isBefore(ctx.gabaritoPreliminarPublicadoEm())
                && !ctx.protocoladoEm().isAfter(limite);
            return tempestivo
                ? ResultadoAvaliacao.ok()
                : ResultadoAvaliacao.falha("Recurso intempestivo: fora da janela regulamentar pos-gabarito preliminar.");
        };
    }

    private static Specification<ContextoAberturaRecurso> unicidadePorQuestaoECandidatoSpec() {
        return ctx -> (ctx.regrasEdital().limitarUmRecursoPorQuestaoPorCandidato() && ctx.candidatoJaRecorreuDestaQuestao())
            ? ResultadoAvaliacao.falha("O edital permite apenas 1 recurso por questao para o mesmo candidato.")
            : ResultadoAvaliacao.ok();
    }

    private static Specification<ContextoAberturaRecurso> tetoGlobalRecursosPorCandidatoSpec() {
        return ctx -> {
            Integer limite = ctx.regrasEdital().limiteMaximoRecursosPorCandidato();
            if (limite != null && ctx.quantidadeRecursosDoCandidatoNoConcurso() >= limite) {
                return ResultadoAvaliacao.falha(
                    "Limite maximo de " + limite + " recursos por candidato atingido neste concurso."
                );
            }
            return ResultadoAvaliacao.ok();
        };
    }

    private static Specification<ContextoAberturaRecurso> fundamentacaoConsistenteSpec() {
        return ctx -> (ctx.fundamentacao() != null && ctx.fundamentacao().trim().length() >= 15)
            ? ResultadoAvaliacao.ok()
            : ResultadoAvaliacao.falha("A fundamentacao do recurso deve conter argumentacao minima de 15 caracteres.");
    }
}
