package ru.practicum.ewm.stats.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.ewm.stats.model.EndpointHit;

@Mapper(componentModel = "spring")
public interface EndpointHitMapper {
    String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Mapping(target = "timestamp", source = "timestamp", dateFormat = DATE_PATTERN)
    EndpointHit toEntity(EndpointHitDto dto);

    @Mapping(target = "timestamp", source = "timestamp", dateFormat = DATE_PATTERN)
    EndpointHitDto toDto(EndpointHit entity);
}

