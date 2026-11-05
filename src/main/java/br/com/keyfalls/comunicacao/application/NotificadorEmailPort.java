package br.com.keyfalls.comunicacao.application;

import br.com.keyfalls.comunicacao.domain.EnviadorEmailPort;

/**
 * Secondary Output Port da camada application do contexto Comunicacao para envio
 * de notificacoes transacionais por e-mail (implementado por JavaMailNotificadorAdapter).
 */
public interface NotificadorEmailPort extends EnviadorEmailPort {
}
