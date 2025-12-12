package ru.practicum.compilation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.compilation.dto.CompilationDto;
import ru.practicum.compilation.dto.NewCompilationDto;
import ru.practicum.compilation.dto.UpdateCompilationRequest;
import ru.practicum.compilation.mapper.CompilationMapper;
import ru.practicum.compilation.Compilation;
import ru.practicum.compilation.repository.CompilationRepository;
import ru.practicum.exception.NotFoundException;

import ru.practicum.event.repository.EventRepository;
import ru.practicum.event.Event;

import java.util.List;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;

    @Override
    @Transactional
    public CompilationDto createCompilation(NewCompilationDto newCompilationDto) {
        log.info("Creating new compilation with title: {}", newCompilationDto.getTitle());

        Compilation compilation = compilationMapper.toEntity(newCompilationDto);
        if (newCompilationDto.getEvents() != null && !newCompilationDto.getEvents().isEmpty()) {
            List<Event> events = eventRepository.findAllById(newCompilationDto.getEvents());
            compilation.setEvents(new HashSet<>(events));
        }

        Compilation savedCompilation = compilationRepository.save(compilation);

        return compilationMapper.toDto(savedCompilation);
    }

    @Override
    @Transactional
    public void deleteCompilation(Long compId) {
        log.info("Deleting compilation with id: {}", compId);

        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Compilation with id=" + compId + " was not found");
        }

        compilationRepository.deleteById(compId);
    }

    @Override
    @Transactional
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        log.info("Updating compilation with id: {}", compId);

        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));

        if (updateRequest.getTitle() != null) {
            compilation.setTitle(updateRequest.getTitle());
        }

        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }

        if (updateRequest.getEvents() != null) {
            List<Event> events = eventRepository.findAllById(updateRequest.getEvents());
            compilation.setEvents(new HashSet<>(events));
        }

        Compilation updatedCompilation = compilationRepository.save(compilation);
        return compilationMapper.toDto(updatedCompilation);
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, Pageable pageable) {
        log.info("Getting compilations with pinned={}", pinned);

        if (pinned != null) {
            return compilationRepository.findAllByPinned(pinned, pageable)
                    .stream()
                    .map(compilationMapper::toDto)
                    .collect(Collectors.toList());
        }

        return compilationRepository.findAll(pageable)
                .stream()
                .map(compilationMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public CompilationDto getCompilationById(Long compId) {
        log.info("Getting compilation with id: {}", compId);

        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));

        return compilationMapper.toDto(compilation);
    }
}
