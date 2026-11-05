package br.com.keyfalls.inscricao.infrastructure.persistence;

import br.com.keyfalls.inscricao.domain.Boleto;
import br.com.keyfalls.inscricao.domain.Inscricao;
import br.com.keyfalls.inscricao.domain.PedidoIsencao;
import br.com.keyfalls.inscricao.domain.SituacaoIsencao;
import br.com.keyfalls.inscricao.domain.StatusInscricao;
import br.com.keyfalls.shared.domain.CandidatoId;
import br.com.keyfalls.shared.domain.CargoId;
import br.com.keyfalls.shared.domain.ConcursoId;
import br.com.keyfalls.shared.domain.InscricaoId;
import br.com.keyfalls.shared.domain.NumeroInscricao;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * Mapper MapStruct na camada infrastructure: traduz o AggregateRoot rico (sem setters
 * e com accessors fluentes em estilo record) de/para a entidade JPA gerenciada pelo Hibernate.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InscricaoPersistenceMapper {

    @Mapping(target = "id", expression = "java(domain.id().valor())")
    @Mapping(target = "numeroInscricao", expression = "java(domain.numero().codigo())")
    @Mapping(target = "candidatoId", expression = "java(domain.candidatoId().valor())")
    @Mapping(target = "concursoId", expression = "java(domain.concursoId().valor())")
    @Mapping(target = "cargoId", expression = "java(domain.cargoId().valor())")
    @Mapping(target = "status", expression = "java(domain.status().name())")
    @Mapping(target = "boletoLinhaDigitavel", expression = "java(domain.boleto() != null ? domain.boleto().linhaDigitavel() : null)")
    @Mapping(target = "boletoValor", expression = "java(domain.boleto() != null ? domain.boleto().valor() : null)")
    @Mapping(target = "boletoVencimento", expression = "java(domain.boleto() != null ? domain.boleto().vencimento() : null)")
    @Mapping(target = "boletoPago", expression = "java(domain.boleto() != null && domain.boleto().pago())")
    @Mapping(target = "boletoLiquidadoEm", expression = "java(domain.boleto() != null ? domain.boleto().liquidadoEm() : null)")
    @Mapping(target = "isencaoJustificativa", expression = "java(domain.pedidoIsencao() != null ? domain.pedidoIsencao().justificativaLegal() : null)")
    @Mapping(target = "isencaoSolicitadaEm", expression = "java(domain.pedidoIsencao() != null ? domain.pedidoIsencao().solicitadoEm() : null)")
    @Mapping(target = "isencaoSituacao", expression = "java(domain.pedidoIsencao() != null ? domain.pedidoIsencao().situacao().name() : null)")
    @Mapping(target = "isencaoParecer", expression = "java(domain.pedidoIsencao() != null ? domain.pedidoIsencao().parecerAvaliacao() : null)")
    @Mapping(target = "isencaoAvaliadaEm", expression = "java(domain.pedidoIsencao() != null ? domain.pedidoIsencao().avaliadoEm() : null)")
    InscricaoJpaEntity toJpaEntity(Inscricao domain);

    default Inscricao toDomain(InscricaoJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Boleto boleto = entity.getBoletoLinhaDigitavel() != null
            ? new Boleto(
                entity.getBoletoLinhaDigitavel(),
                entity.getBoletoValor(),
                entity.getBoletoVencimento(),
                entity.isBoletoPago(),
                entity.getBoletoLiquidadoEm()
            )
            : null;

        PedidoIsencao isencao = entity.getIsencaoJustificativa() != null
            ? PedidoIsencao.reconstituir(
                entity.getIsencaoJustificativa(),
                entity.getIsencaoSolicitadaEm(),
                SituacaoIsencao.valueOf(entity.getIsencaoSituacao()),
                entity.getIsencaoParecer(),
                entity.getIsencaoAvaliadaEm()
            )
            : null;

        return Inscricao.reconstituir(
            new InscricaoId(entity.getId()),
            new NumeroInscricao(entity.getNumeroInscricao()),
            new CandidatoId(entity.getCandidatoId()),
            new ConcursoId(entity.getConcursoId()),
            new CargoId(entity.getCargoId()),
            StatusInscricao.valueOf(entity.getStatus()),
            boleto,
            isencao
        );
    }
}
