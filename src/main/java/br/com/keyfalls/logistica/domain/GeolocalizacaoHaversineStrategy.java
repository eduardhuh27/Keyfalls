package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.Endereco;

import java.util.Comparator;
import java.util.Objects;

/**
 * Etapa 5 (Fase 2 - v2): Ordena locais pela menor distancia esferica real em quilometros
 * (formula de Haversine sobre latitude/longitude), mantendo fallback seguro por bairro/CEP
 * caso algum cadastro legado nao possua coordenadas geocodificadas.
 */
public final class GeolocalizacaoHaversineStrategy implements EstrategiaProximidadeAlocacao {

    @Override
    public Comparator<LocalDeProva> comparadorDeProximidadePara(Endereco enderecoCandidato) {
        Objects.requireNonNull(enderecoCandidato, "Endereco do candidato obrigatorio.");
        return Comparator
            .comparingDouble((LocalDeProva local) -> enderecoCandidato.distanciaAproximadaEmKm(local.endereco()))
            .thenComparing(local -> !enderecoCandidato.mesmoBairroECidade(local.endereco()));
    }
}
