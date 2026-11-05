package br.com.keyfalls.inscricao.domain;

import br.com.keyfalls.shared.domain.ConfiguracaoRegrasEdital;
import br.com.keyfalls.shared.domain.ModalidadeIsencao;
import br.com.keyfalls.shared.domain.Periodo;
import br.com.keyfalls.shared.domain.exception.PrazoIsencaoEncerradoException;
import br.com.keyfalls.shared.domain.specification.Specification;
import br.com.keyfalls.shared.domain.specification.Specification.ResultadoAvaliacao;

import java.time.Instant;
import java.util.Objects;

/**
 * Specification composavel para validar a elegibilidade de solicitacao de isencao
 * conforme as regras parametrizadas no edital do Concurso.
 */
public final class EspecificacaoElegibilidadeIsencao {

    public record ContextoSolicitacaoIsencao(
        ModalidadeIsencao modalidadeSolicitada,
        String justificativaLegal,
        boolean possuiComprovanteDocumentalAnexo,
        boolean nisCadUnicoValidadoNaBaseGov,
        Instant instanteSolicitacao,
        Periodo periodoIsencaoEdital,
        ConfiguracaoRegrasEdital regrasEdital
    ) {
        public ContextoSolicitacaoIsencao {
            Objects.requireNonNull(modalidadeSolicitada, "Modalidade de isencao obrigatoria.");
            Objects.requireNonNull(instanteSolicitacao, "Instante da solicitacao obrigatorio.");
            Objects.requireNonNull(periodoIsencaoEdital, "Periodo de isencao do edital obrigatorio.");
            Objects.requireNonNull(regrasEdital, "Configuracao de regras do edital obrigatoria.");
        }
    }

    private EspecificacaoElegibilidadeIsencao() {}

    public static Specification<ContextoSolicitacaoIsencao> construirParaEdital() {
        return dentroDoPrazoEstritoSpec()
            .and(modalidadeContempladaNoEditalSpec())
            .and(comprovacaoDocumentalOuGovBrSpec());
    }

    public static void validarOuRejeitar(ContextoSolicitacaoIsencao contexto) {
        if (!contexto.periodoIsencaoEdital().contemEstritamente(contexto.instanteSolicitacao())) {
            throw new PrazoIsencaoEncerradoException(
                "Solicitacao de isencao fora do periodo estrito estabelecido no edital."
            );
        }
        construirParaEdital().validarOuLancar(contexto);
    }

    private static Specification<ContextoSolicitacaoIsencao> dentroDoPrazoEstritoSpec() {
        return ctx -> ctx.periodoIsencaoEdital().contemEstritamente(ctx.instanteSolicitacao())
            ? ResultadoAvaliacao.ok()
            : ResultadoAvaliacao.falha("Solicitacao de isencao fora do prazo regulamentar do edital.");
    }

    private static Specification<ContextoSolicitacaoIsencao> modalidadeContempladaNoEditalSpec() {
        return ctx -> ctx.regrasEdital().modalidadesIsencaoAceitas().contains(ctx.modalidadeSolicitada())
            ? ResultadoAvaliacao.ok()
            : ResultadoAvaliacao.falha(
                "A modalidade de isencao " + ctx.modalidadeSolicitada() + " nao e prevista neste edital."
            );
    }

    private static Specification<ContextoSolicitacaoIsencao> comprovacaoDocumentalOuGovBrSpec() {
        return ctx -> {
            if (ctx.modalidadeSolicitada() == ModalidadeIsencao.CAD_UNICO_BAIXA_RENDA
                && ctx.regrasEdital().validacaoAutomatizadaCadUnico()) {
                return ctx.nisCadUnicoValidadoNaBaseGov()
                    ? ResultadoAvaliacao.ok()
                    : ResultadoAvaliacao.falha("NIS nao localizado ou inativo na consulta automatizada do CadUnico.");
            }
            return ctx.possuiComprovanteDocumentalAnexo()
                ? ResultadoAvaliacao.ok()
                : ResultadoAvaliacao.falha("Comprovante documental obrigatorio para analise da comissao de isencao.");
        };
    }
}
