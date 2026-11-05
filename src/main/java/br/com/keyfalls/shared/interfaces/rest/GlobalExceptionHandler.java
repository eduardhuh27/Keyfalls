package br.com.keyfalls.shared.interfaces.rest;

import br.com.keyfalls.shared.domain.RegraDeNegocioException;
import br.com.keyfalls.shared.domain.exception.CapacidadeSalaExcedidaException;
import br.com.keyfalls.shared.domain.exception.CargoIncompativelNaSalaException;
import br.com.keyfalls.shared.domain.exception.ConflitoDeEstadoException;
import br.com.keyfalls.shared.domain.exception.EntidadeNaoEncontradaException;
import br.com.keyfalls.shared.domain.exception.PrazoIsencaoEncerradoException;
import br.com.keyfalls.shared.domain.exception.PropriedadeRecursoVioladaException;
import br.com.keyfalls.shared.domain.exception.RecursosPendentesException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tratador global padronizado segundo a RFC 7807 / RFC 9457 (ProblemDetail nativo do Spring Boot 3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Erro de Validacao Sintatica",
            "Um ou mais campos da requisicao sao invalidos.",
            "https://api.keyfalls.com.br/erros/validacao-sintatica",
            request
        );

        Map<String, String> violacoes = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            violacoes.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("violacoes", violacoes);
        return problem;
    }

    @ExceptionHandler({IllegalArgumentException.class, MaxUploadSizeExceededException.class})
    public ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
        return buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Requisicao Malformada ou Arquivo Excedente",
            ex.getMessage(),
            "https://api.keyfalls.com.br/erros/requisicao-invalida",
            request
        );
    }

    @ExceptionHandler({AccessDeniedException.class, PropriedadeRecursoVioladaException.class})
    public ProblemDetail handleForbidden(RuntimeException ex, HttpServletRequest request) {
        return buildProblemDetail(
            HttpStatus.FORBIDDEN,
            "Acesso Negado ao Recurso",
            ex.getMessage(),
            "https://api.keyfalls.com.br/erros/acesso-negado",
            request
        );
    }

    @ExceptionHandler(EntidadeNaoEncontradaException.class)
    public ProblemDetail handleNotFound(EntidadeNaoEncontradaException ex, HttpServletRequest request) {
        return buildProblemDetail(
            HttpStatus.NOT_FOUND,
            "Recurso Nao Encontrado",
            ex.getMessage(),
            "https://api.keyfalls.com.br/erros/nao-encontrado",
            request
        );
    }

    @ExceptionHandler({
        CapacidadeSalaExcedidaException.class,
        RecursosPendentesException.class,
        ConflitoDeEstadoException.class,
        ObjectOptimisticLockingFailureException.class,
        DataIntegrityViolationException.class
    })
    public ProblemDetail handleConflict(RuntimeException ex, HttpServletRequest request) {
        return buildProblemDetail(
            HttpStatus.CONFLICT,
            "Conflito de Estado ou Concorrencia de Negocio",
            ex.getMessage(),
            "https://api.keyfalls.com.br/erros/conflito-negocio",
            request
        );
    }

    @ExceptionHandler({
        PrazoIsencaoEncerradoException.class,
        CargoIncompativelNaSalaException.class,
        RegraDeNegocioException.class
    })
    public ProblemDetail handleUnprocessableEntity(RegraDeNegocioException ex, HttpServletRequest request) {
        return buildProblemDetail(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "Violacao de Invariante de Dominio",
            ex.getMessage(),
            "https://api.keyfalls.com.br/erros/regra-de-negocio",
            request
        );
    }

    private static ProblemDetail buildProblemDetail(
        HttpStatus status,
        String title,
        String detail,
        String typeUri,
        HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(typeUri));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
