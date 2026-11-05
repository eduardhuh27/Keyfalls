package br.com.keyfalls.inscricao.interfaces.rest.mapper;

import br.com.keyfalls.inscricao.application.InscricaoRealizadaOutput;
import br.com.keyfalls.inscricao.application.RealizarInscricaoCommand;
import br.com.keyfalls.inscricao.interfaces.rest.dto.InscricaoResponse;
import br.com.keyfalls.inscricao.interfaces.rest.dto.RealizarInscricaoRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.UUID;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface InscricaoApiMapper {

    @Mapping(target = "candidatoId", source = "candidatoAutenticadoId")
    @Mapping(target = "concursoId", source = "request.concursoId")
    @Mapping(target = "cargoId", source = "request.cargoId")
    RealizarInscricaoCommand toCommand(UUID candidatoAutenticadoId, RealizarInscricaoRequest request);

    InscricaoResponse toResponse(InscricaoRealizadaOutput output);
}
