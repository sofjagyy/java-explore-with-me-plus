package ru.practicum.compilation.mapper;

import lombok.experimental.UtilityClass;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.Compilation;

import java.util.Set;

@UtilityClass
public class CompilationMapper {

    public static Compilation toCompilation(NewCompilationDto newCompilationDto) {
        Compilation compilation = new Compilation();
        compilation.setTitle(newCompilationDto.getTitle());
        compilation.setPinned(newCompilationDto.getPinned() != null ? newCompilationDto.getPinned() : false);

        if (newCompilationDto.getEvents() != null) {
            compilation.setEventIds((Set<Long>) newCompilationDto.getEvents());
        }

        return compilation;
    }

    public static CompilationDto toCompilationDto(Compilation compilation) {
        return new CompilationDto(
                compilation.getId(),
                compilation.getEventIds(),
                compilation.getPinned(),
                compilation.getTitle()
        );
    }
}