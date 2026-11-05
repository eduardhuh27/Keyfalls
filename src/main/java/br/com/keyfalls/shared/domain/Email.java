package br.com.keyfalls.shared.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String endereco) {

    private static final Pattern PADRAO_EMAIL =
        Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public Email {
        Objects.requireNonNull(endereco, "E-mail obrigatorio.");
        String normalizado = endereco.trim().toLowerCase(Locale.ROOT);
        if (!PADRAO_EMAIL.matcher(normalizado).matches()) {
            throw new RegraDeNegocioException("Formato de e-mail invalido: " + endereco);
        }
        endereco = normalizado;
    }
}
