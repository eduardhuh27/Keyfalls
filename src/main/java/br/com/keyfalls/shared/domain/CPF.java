package br.com.keyfalls.shared.domain;

import java.util.Objects;

public record CPF(String numero) {

    public CPF {
        Objects.requireNonNull(numero, "CPF obrigatorio.");
        String digitos = numero.replaceAll("\\D", "");
        if (digitos.length() != 11 || digitos.chars().distinct().count() == 1) {
            throw new RegraDeNegocioException("CPF em formato invalido.");
        }
        if (!digitosVerificadoresValidos(digitos)) {
            throw new RegraDeNegocioException("Digitos verificadores do CPF invalidos.");
        }
        numero = digitos;
    }

    private static boolean digitosVerificadoresValidos(String cpf) {
        int primeiroDigito = calcularDigito(cpf, 9, 10);
        int segundoDigito = calcularDigito(cpf, 10, 11);
        return (cpf.charAt(9) - '0') == primeiroDigito
            && (cpf.charAt(10) - '0') == segundoDigito;
    }

    private static int calcularDigito(String cpf, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (cpf.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = (soma * 10) % 11;
        return (resto == 10) ? 0 : resto;
    }

    public String formatado() {
        return "%s.%s.%s-%s".formatted(
            numero.substring(0, 3),
            numero.substring(3, 6),
            numero.substring(6, 9),
            numero.substring(9, 11)
        );
    }
}
