package br.com.keyfalls.shared.domain.security;

public enum PerfilAcesso {
    ROLE_CANDIDATO,
    ROLE_FISCAL,
    ROLE_ADMIN;

    public String authority() {
        return this.name();
    }
}
