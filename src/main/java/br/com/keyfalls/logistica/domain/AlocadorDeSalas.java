package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.AlocacaoDeSalaId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Domain Service responsavel por distribuir candidatos homologados nas salas disponiveis
 * do mesmo cargo, priorizando mesmo bairro/cidade e menor distancia geografica.
 */
public final class AlocadorDeSalas {

    private final EstrategiaProximidadeAlocacao estrategiaProximidade;

    public AlocadorDeSalas() {
        this(new BairroCidadeMatchingStrategy());
    }

    public AlocadorDeSalas(EstrategiaProximidadeAlocacao estrategiaProximidade) {
        this.estrategiaProximidade = Objects.requireNonNull(
            estrategiaProximidade,
            "Estrategia de proximidade obrigatoria."
        );
    }

    public record CandidatoHomologadoParaAlocacao(
        InscricaoId inscricaoId,
        CargoId cargoId,
        Endereco enderecoResidencial
    ) {
        public CandidatoHomologadoParaAlocacao {
            Objects.requireNonNull(inscricaoId, "InscricaoId obrigatorio para alocacao.");
            Objects.requireNonNull(cargoId, "CargoId obrigatorio para alocacao.");
            Objects.requireNonNull(enderecoResidencial, "Endereco do candidato obrigatorio para alocacao.");
        }
    }

    public List<AlocacaoDeSala> distribuirCandidatos(
        List<CandidatoHomologadoParaAlocacao> candidatosHomologados,
        List<LocalDeProva> locaisDisponiveis
    ) {
        Objects.requireNonNull(candidatosHomologados, "Lista de candidatos obrigatoria.");
        Objects.requireNonNull(locaisDisponiveis, "Lista de locais de prova obrigatoria.");

        List<AlocacaoDeSala> alocacoesGeradas = new ArrayList<>();

        for (CandidatoHomologadoParaAlocacao candidato : candidatosHomologados) {
            Comparator<LocalDeProva> porProximidade =
                estrategiaProximidade.comparadorDeProximidadePara(candidato.enderecoResidencial());

            LocalDeProva localEscolhido = locaisDisponiveis.stream()
                .filter(local -> local.encontrarPrimeiraSalaDisponivelParaCargo(candidato.cargoId()).isPresent())
                .min(porProximidade)
                .orElseThrow(() -> new RegraDeNegocioException(
                    "Nao ha sala disponivel com capacidade para o cargo %s (Inscricao: %s)."
                        .formatted(candidato.cargoId().valor(), candidato.inscricaoId().valor())
                ));

            Sala salaDisponivel = localEscolhido
                .encontrarPrimeiraSalaDisponivelParaCargo(candidato.cargoId())
                .orElseThrow();

            localEscolhido.alocarCandidatoEmSalaEspecifica(
                salaDisponivel.id(),
                candidato.inscricaoId(),
                candidato.cargoId()
            );

            AlocacaoDeSala alocacao = AlocacaoDeSala.vincular(
                AlocacaoDeSalaId.novo(),
                candidato.inscricaoId(),
                candidato.cargoId(),
                localEscolhido.id(),
                salaDisponivel.id()
            );
            alocacoesGeradas.add(alocacao);
        }

        return Collections.unmodifiableList(alocacoesGeradas);
    }
}
