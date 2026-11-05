package br.com.keyfalls.identidade.domain;

import br.com.keyfalls.shared.domain.AggregateRoot;
import br.com.keyfalls.shared.domain.CPF;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.Email;
import br.com.keyfalls.shared.domain.Endereco;
import br.com.keyfalls.shared.domain.RegraDeNegocioException;

import java.time.Instant;
import java.util.Objects;

public final class Candidato extends AggregateRoot<CandidatoId> {

    private final CandidatoId id;
    private final CPF cpf;
    private String nomeCompleto;
    private Email email;
    private Endereco endereco;
    private Credencial credencial;

    private Candidato(
        CandidatoId id,
        String nomeCompleto,
        CPF cpf,
        Email email,
        Endereco endereco,
        Credencial credencial
    ) {
        this.id = Objects.requireNonNull(id, "CandidatoId obrigatorio.");
        if (nomeCompleto == null || nomeCompleto.trim().length() < 3) {
            throw new RegraDeNegocioException("Nome completo do candidato deve possuir ao menos 3 caracteres.");
        }
        this.nomeCompleto = nomeCompleto.trim();
        this.cpf = Objects.requireNonNull(cpf, "CPF do candidato obrigatorio.");
        this.email = Objects.requireNonNull(email, "E-mail do candidato obrigatorio.");
        this.endereco = Objects.requireNonNull(endereco, "Endereco do candidato obrigatorio.");
        this.credencial = Objects.requireNonNull(credencial, "Credencial do candidato obrigatoria.");
    }

    public static Candidato cadastrar(
        CandidatoId id,
        String nomeCompleto,
        CPF cpf,
        Email email,
        Endereco endereco,
        String senhaHash,
        Instant agora
    ) {
        return new Candidato(id, nomeCompleto, cpf, email, endereco, Credencial.ativa(senhaHash, agora));
    }

    public static Candidato reconstituir(
        CandidatoId id,
        String nomeCompleto,
        CPF cpf,
        Email email,
        Endereco endereco,
        Credencial credencial
    ) {
        return new Candidato(id, nomeCompleto, cpf, email, endereco, credencial);
    }

    public void atualizarDadosContato(Email novoEmail, Endereco novoEndereco) {
        this.email = Objects.requireNonNull(novoEmail, "Novo e-mail obrigatorio.");
        this.endereco = Objects.requireNonNull(novoEndereco, "Novo endereco obrigatorio.");
    }

    public void redefinirSenha(String novoSenhaHash, Instant agora) {
        if (credencial.bloqueada()) {
            throw new RegraDeNegocioException("Nao e permitido redefinir senha de conta bloqueada.");
        }
        this.credencial = Credencial.ativa(novoSenhaHash, agora);
    }

    public void bloquearAcesso(Instant agora) {
        this.credencial = this.credencial.bloquear(agora);
    }

    @Override
    public CandidatoId id() {
        return id;
    }

    public String nomeCompleto() {
        return nomeCompleto;
    }

    public CPF cpf() {
        return cpf;
    }

    public Email email() {
        return email;
    }

    public Endereco endereco() {
        return endereco;
    }

    public Credencial credencial() {
        return credencial;
    }
}
