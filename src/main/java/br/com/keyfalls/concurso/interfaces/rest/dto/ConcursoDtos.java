package br.com.keyfalls.concurso.interfaces.rest.dto;

import br.com.keyfalls.concurso.application.ConcursoUseCases;
import br.com.keyfalls.shared.domain.Alternativa;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConcursoDtos {

    private ConcursoDtos() {}

    public record CriarConcursoRequest(
        @NotBlank(message = "O codigo do edital e obrigatorio.")
        String codigoEdital,

        @NotBlank(message = "O titulo do concurso e obrigatorio.")
        String titulo,

        @NotBlank(message = "A abrangencia (NACIONAL, ESTADUAL, MUNICIPAL) e obrigatoria.")
        String abrangencia,

        @NotNull(message = "O inicio do periodo de inscricao e obrigatorio.")
        @JsonAlias({"periodoInscricaoInicio", "inicioInscricao"})
        Instant inicioInscricao,

        @NotNull(message = "O fim do periodo de inscricao e obrigatorio.")
        @JsonAlias({"periodoInscricaoFim", "fimInscricao"})
        Instant fimInscricao,

        @NotNull(message = "O inicio do periodo de isencao e obrigatorio.")
        @JsonAlias({"periodoIsencaoInicio", "inicioIsencao"})
        Instant inicioIsencao,

        @NotNull(message = "O fim do periodo de isencao e obrigatorio.")
        @JsonAlias({"periodoIsencaoFim", "fimIsencao"})
        Instant fimIsencao,

        @NotBlank(message = "A politica de anulacao de questoes e obrigatoria.")
        String politicaAnulacao
    ) {
        public ConcursoUseCases.CriarConcursoCommand toCommand() {
            return new ConcursoUseCases.CriarConcursoCommand(
                codigoEdital, titulo, abrangencia,
                inicioInscricao, fimInscricao, inicioIsencao, fimIsencao, politicaAnulacao
            );
        }
    }

    public record ConcursoResponse(
        UUID concursoId,
        String codigoEdital,
        String titulo,
        String abrangencia
    ) {
        public static ConcursoResponse from(ConcursoUseCases.ConcursoCriadoOutput out) {
            return new ConcursoResponse(out.concursoId(), out.codigoEdital(), out.titulo(), out.abrangencia());
        }
    }

    public record ProvaCargoRequest(
        @NotBlank(message = "O nome da disciplina/prova e obrigatorio.")
        String disciplina,

        @NotNull(message = "O peso da prova e obrigatorio.")
        @Positive(message = "O peso da prova deve ser estritamente positivo.")
        BigDecimal peso
    ) {}

    public record AdicionarCargoRequest(
        @NotBlank(message = "O nome do cargo e obrigatorio.")
        @JsonAlias({"nome", "nomeCargo"})
        String nomeCargo,

        @Positive(message = "A quantidade de vagas deve ser maior que zero.")
        int vagas,

        @NotNull(message = "A taxa de inscricao e obrigatoria.")
        @PositiveOrZero(message = "A taxa de inscricao nao pode ser negativa.")
        BigDecimal taxaInscricao,

        @NotEmpty(message = "O cargo deve possuir ao menos uma prova configurada.")
        @Valid
        List<ProvaCargoRequest> provas
    ) {
        public ConcursoUseCases.AdicionarCargoComProvasCommand toCommand(UUID concursoId) {
            var provasInput = provas.stream()
                .map(p -> new ConcursoUseCases.ProvaInicialInput(p.disciplina(), p.peso()))
                .toList();
            return new ConcursoUseCases.AdicionarCargoComProvasCommand(
                concursoId, nomeCargo, vagas, taxaInscricao, provasInput
            );
        }
    }

    public record CargoCriadoResponse(
        UUID cargoId,
        String nomeCargo,
        List<UUID> provasIds
    ) {
        public static CargoCriadoResponse from(ConcursoUseCases.CargoComProvasOutput out) {
            return new CargoCriadoResponse(out.cargoId(), out.nomeCargo(), out.provasCriadasIds());
        }
    }

    public record NovaQuestaoRequest(
        @Positive(message = "O numero de ordem da questao deve ser positivo.")
        int numeroOrdem,

        @NotBlank(message = "O enunciado da questao e obrigatorio.")
        String enunciado,

        @NotNull(message = "As alternativas da questao sao obrigatorias.")
        @Size(min = 2, max = 5, message = "A questao deve conter entre 2 e 5 alternativas.")
        Map<Alternativa, @NotBlank String> opcoes
    ) {}

    public record CadastrarQuestoesEmLoteRequest(
        @NotEmpty(message = "Informe ao menos uma questao para cadastro.")
        @Valid
        List<NovaQuestaoRequest> questoes
    ) {
        public ConcursoUseCases.CadastrarQuestoesCommand toCommand(UUID provaId) {
            var itens = questoes.stream()
                .map(q -> new ConcursoUseCases.CadastrarQuestoesCommand.ItemQuestaoInput(
                    q.numeroOrdem(), q.enunciado(), q.opcoes()
                ))
                .toList();
            return new ConcursoUseCases.CadastrarQuestoesCommand(provaId, itens);
        }
    }

    public record QuestoesCadastradasResponse(UUID provaId, List<UUID> questoesIds) {
        public static QuestoesCadastradasResponse from(ConcursoUseCases.QuestoesCadastradasOutput out) {
            return new QuestoesCadastradasResponse(out.provaId(), out.questoesIds());
        }
    }

    public record PublicarGabaritoRequest(
        boolean definitivo,

        @NotEmpty(message = "O mapa de respostas oficiais nao pode ser vazio.")
        Map<@NotNull UUID, @NotNull Alternativa> respostasOficiais
    ) {
        public ConcursoUseCases.PublicarGabaritoCommand toCommand(UUID provaId) {
            return new ConcursoUseCases.PublicarGabaritoCommand(provaId, definitivo, respostasOficiais);
        }
    }

    public record GabaritoPublicadoResponse(
        UUID provaId,
        boolean definitivo,
        int totalQuestoes,
        Instant publicadoEm
    ) {
        public static GabaritoPublicadoResponse from(ConcursoUseCases.GabaritoPublicadoOutput out) {
            return new GabaritoPublicadoResponse(out.provaId(), out.definitivo(), out.totalQuestoes(), out.publicadoEm());
        }
    }
}
