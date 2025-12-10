package ru.practicum.compilation.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.compilation.Compilation;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.event.dto.EventShortDto;
import ru.practicum.event.mapper.EventMapper;

import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class CompilationMapper {

    public static Compilation toCompilation(NewCompilationDto newCompilationDto) {
        Compilation compilation = new Compilation();
        compilation.setTitle(newCompilationDto.getTitle());
        compilation.setPinned(newCompilationDto.getPinned() != null ? newCompilationDto.getPinned() : false);
        return compilation;
    }

    public static CompilationDto toCompilationDto(Compilation compilation, EventMapper eventMapper) {
        List<EventShortDto> events = compilation.getEvents().stream()
                .map(eventMapper::toEventShortDto)
                .collect(Collectors.toList());

        return new CompilationDto(
                compilation.getId(),
                events,
                compilation.getPinned(),
                compilation.getTitle()
        );
    }
}