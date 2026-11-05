package br.com.keyfalls.logistica.domain;

import br.com.keyfalls.shared.domain.Endereco;

import java.util.Comparator;

/**
 * Strategy de Dominio para ordenacao de locais de prova por proximidade.
 * Permite evoluir da Fase 1 (correspondencia textual Cidade/Bairro) para a Fase 2
 * (geolocalizacao Haversine por coordenadas) respeitando o Open-Closed Principle (OCP).
 */
public interface EstrategiaProximidadeAlocacao {

    Comparator<LocalDeProva> comparadorDeProximidadePara(Endereco enderecoCandidato);
}
