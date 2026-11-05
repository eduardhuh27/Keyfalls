package br.com.keyfalls.comunicacao.domain;

public interface EnviadorEmailPort {
    void enviar(MensagemTransacional mensagem);
}
