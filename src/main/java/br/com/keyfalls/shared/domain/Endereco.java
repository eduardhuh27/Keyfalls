package br.com.keyfalls.shared.domain;

import java.util.Locale;
import java.util.Objects;

public record Endereco(
    String logradouro,
    String numero,
    String bairro,
    String cidade,
    String uf,
    String cep,
    Double latitude,
    Double longitude
) {

    public Endereco {
        logradouro = validarTextoObrigatorio(logradouro, "Logradouro");
        numero = validarTextoObrigatorio(numero, "Numero");
        bairro = validarTextoObrigatorio(bairro, "Bairro");
        cidade = validarTextoObrigatorio(cidade, "Cidade");
        uf = validarTextoObrigatorio(uf, "UF").toUpperCase(Locale.ROOT);
        if (uf.length() != 2) {
            throw new RegraDeNegocioException("UF deve conter exatamente 2 letras.");
        }
        Objects.requireNonNull(cep, "CEP obrigatorio.");
        String cepLimpo = cep.replaceAll("\\D", "");
        if (cepLimpo.length() != 8) {
            throw new RegraDeNegocioException("CEP deve conter exatamente 8 digitos.");
        }
        cep = cepLimpo;

        if ((latitude == null) != (longitude == null)) {
            throw new RegraDeNegocioException("Latitude e longitude devem ser informadas em conjunto.");
        }
        if (latitude != null && (latitude < -90.0 || latitude > 90.0 || longitude < -180.0 || longitude > 180.0)) {
            throw new RegraDeNegocioException("Coordenadas geograficas fora dos limites validos.");
        }
    }

    public Endereco(String logradouro, String numero, String bairro, String cidade, String uf, String cep) {
        this(logradouro, numero, bairro, cidade, uf, cep, null, null);
    }

    public boolean mesmoBairroECidade(Endereco outro) {
        Objects.requireNonNull(outro, "Endereco de comparacao obrigatorio.");
        return mesmaCidade(outro) && this.bairro.equalsIgnoreCase(outro.bairro);
    }

    public boolean mesmaCidade(Endereco outro) {
        Objects.requireNonNull(outro, "Endereco de comparacao obrigatorio.");
        return this.uf.equalsIgnoreCase(outro.uf) && this.cidade.equalsIgnoreCase(outro.cidade);
    }

    public double distanciaAproximadaEmKm(Endereco destino) {
        Objects.requireNonNull(destino, "Endereco de destino obrigatorio.");
        if (this.latitude != null && destino.latitude != null) {
            return calcularHaversineKm(this.latitude, this.longitude, destino.latitude, destino.longitude);
        }
        if (mesmoBairroECidade(destino)) {
            return 1.0;
        }
        if (mesmaCidade(destino)) {
            int prefixoOrigem = Integer.parseInt(this.cep.substring(0, 5));
            int prefixoDestino = Integer.parseInt(destino.cep.substring(0, 5));
            return 5.0 + Math.abs(prefixoOrigem - prefixoDestino) * 0.1;
        }
        if (this.uf.equalsIgnoreCase(destino.uf)) {
            return 100.0;
        }
        return 1_000.0;
    }

    private static double calcularHaversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double raioTerraKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * raioTerraKm * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String validarTextoObrigatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new RegraDeNegocioException(campo + " nao pode ser vazio.");
        }
        return valor.trim();
    }
}
