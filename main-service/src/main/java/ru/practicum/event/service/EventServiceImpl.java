package ru.practicum.event.service;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatClient;
import ru.practicum.category.Category;
import ru.practicum.category.repository.CategoryRepository;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStatsDto;
import ru.practicum.event.Event;
import ru.practicum.event.EventState;
import ru.practicum.event.Location;
import ru.practicum.event.QEvent;
import ru.practicum.event.dto.*;
import ru.practicum.event.mapper.EventMapper;
import ru.practicum.event.repository.EventRepository;
import ru.practicum.exception.ConflictException;
import ru.practicum.exception.NotFoundException;
import ru.practicum.exception.ValidationException;
import ru.practicum.request.enums.RequestStatus;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.RequestRepository;
import ru.practicum.user.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final RequestRepository requestRepository;
    private final StatClient statClient;
    private final EventMapper eventMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<EventFullDto> getEventsAdmin(List<Long> users, List<String> states, List<Long> categories,
                                             String rangeStart, String rangeEnd, int from, int size) {
        BooleanBuilder builder = new BooleanBuilder();
        QEvent qEvent = QEvent.event;

        if (users != null && !users.isEmpty()) {
            builder.and(qEvent.initiator.id.in(users));
        }
        if (states != null && !states.isEmpty()) {
            builder.and(qEvent.state.stringValue().in(states));
        }
        if (categories != null && !categories.isEmpty()) {
            builder.and(qEvent.category.id.in(categories));
        }
        if (rangeStart != null) {
            builder.and(qEvent.eventDate.goe(parseTime(rangeStart)));
        }
        if (rangeEnd != null) {
            builder.and(qEvent.eventDate.loe(parseTime(rangeEnd)));
        }

        PageRequest pageRequest = PageRequest.of(from / size, size);
        List<Event> events = eventRepository.findAll(builder, pageRequest).getContent();

        return events.stream()
                .map(this::toEventFullDtoWithStats)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto updateEventAdmin(Long eventId, UpdateEventAdminRequest request) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));

        if (request.getEventDate() != null) {
            if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(1))) {
                throw new ValidationException("Event date must be at least 1 hour from now");
            }
            event.setEventDate(request.getEventDate());
        }

        if (request.getStateAction() != null) {
            if (request.getStateAction() == UpdateEventAdminRequest.StateAction.PUBLISH_EVENT) {
                if (event.getState() != EventState.PENDING) {
                    throw new ConflictException("Cannot publish the event because it's not in the right state: " + event.getState());
                }
                event.setState(EventState.PUBLISHED);
                event.setPublishedOn(LocalDateTime.now());
            } else if (request.getStateAction() == UpdateEventAdminRequest.StateAction.REJECT_EVENT) {
                if (event.getState() == EventState.PUBLISHED) {
                    throw new ConflictException("Cannot reject the event because it's already published");
                }
                event.setState(EventState.CANCELED);
            }
        }

        updateEventCommonFields(event, request.getTitle(), request.getAnnotation(), request.getDescription(),
                request.getCategory(), request.getLocation(), request.getPaid(), request.getParticipantLimit(),
                request.getRequestModeration());

        return toEventFullDtoWithStats(eventRepository.save(event));
    }

    @Override
    public List<EventShortDto> getEventsPublic(String text, List<Long> categories, Boolean paid,
                                               String rangeStart, String rangeEnd, Boolean onlyAvailable,
                                               String sort, int from, int size, HttpServletRequest request) {
        
        sendStat(request);
        
        BooleanBuilder builder = new BooleanBuilder();
        QEvent qEvent = QEvent.event;
        
        builder.and(qEvent.state.eq(EventState.PUBLISHED));
        
        if (text != null && !text.isBlank()) {
            builder.and(qEvent.annotation.containsIgnoreCase(text)
                    .or(qEvent.description.containsIgnoreCase(text)));
        }
        if (categories != null && !categories.isEmpty()) {
            builder.and(qEvent.category.id.in(categories));
        }
        if (paid != null) {
            builder.and(qEvent.paid.eq(paid));
        }
        
        LocalDateTime start = (rangeStart != null) ? parseTime(rangeStart) : LocalDateTime.now();
        LocalDateTime end = (rangeEnd != null) ? parseTime(rangeEnd) : null;
        
        if (end != null && start.isAfter(end)) {
            throw new ValidationException("Start date must be before end date");
        }
        
        builder.and(qEvent.eventDate.goe(start));
        if (end != null) {
            builder.and(qEvent.eventDate.loe(end));
        }

        Sort sortOrder = Sort.by(Sort.Direction.ASC, "eventDate");
        if ("VIEWS".equals(sort)) {
        } else {
            sortOrder = Sort.by(Sort.Direction.ASC, "eventDate");
        }

        PageRequest pageRequest = PageRequest.of(from / size, size, sortOrder);
        List<Event> events = eventRepository.findAll(builder, pageRequest).getContent();
        
        List<EventShortDto> result = new ArrayList<>();
        
        for (Event event : events) {
            EventShortDto dto = toEventShortDtoWithStats(event);
            if (Boolean.TRUE.equals(onlyAvailable)) {
                if (event.getParticipantLimit() == 0 || dto.getConfirmedRequests() < event.getParticipantLimit()) {
                    result.add(dto);
                }
            } else {
                result.add(dto);
            }
        }
        
        if ("VIEWS".equals(sort)) {
             result.sort(Comparator.comparing(EventShortDto::getViews).reversed());
        }
        
        return result;
    }

    @Override
    public EventFullDto getEventPublic(Long id, HttpServletRequest request) {
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Event with id=" + id + " was not found"));
        
        if (event.getState() != EventState.PUBLISHED) {
            throw new NotFoundException("Event must be published");
        }
        
        sendStat(request);
        return toEventFullDtoWithStats(event);
    }

    @Override
    public List<EventShortDto> getEventsUser(Long userId, int from, int size) {
        checkUser(userId);
        PageRequest pageRequest = PageRequest.of(from / size, size);
        return eventRepository.findAllByInitiatorId(userId, pageRequest).stream()
                .map(this::toEventShortDtoWithStats)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventFullDto createEventUser(Long userId, NewEventDto newEventDto) {
        User user = checkUser(userId);
        
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            throw new ValidationException("Event date must be at least 2 hours from now");
        }
        
        Category category = categoryRepository.findById(newEventDto.getCategory())
                .orElseThrow(() -> new NotFoundException("Category not found"));
        
        Event event = eventMapper.toEvent(newEventDto);
        event.setInitiator(user);
        event.setCategory(category);
        event.setState(EventState.PENDING);
        event.setCreatedOn(LocalDateTime.now());
        
        Location location = eventMapper.toLocation(newEventDto.getLocation());
        event.setLocation(location);
        
        return toEventFullDtoWithStats(eventRepository.save(event));
    }

    @Override
    public EventFullDto getEventUser(Long userId, Long eventId) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        return toEventFullDtoWithStats(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventUser(Long userId, Long eventId, UpdateEventUserRequest request) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        
        if (event.getState() == EventState.PUBLISHED) {
            throw new ConflictException("Only pending or canceled events can be changed");
        }
        
        if (request.getEventDate() != null) {
             if (request.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                throw new ValidationException("Event date must be at least 2 hours from now");
            }
             event.setEventDate(request.getEventDate());
        }
        
        if (request.getStateAction() != null) {
            if (request.getStateAction() == UpdateEventUserRequest.StateAction.SEND_TO_REVIEW) {
                event.setState(EventState.PENDING);
            } else if (request.getStateAction() == UpdateEventUserRequest.StateAction.CANCEL_REVIEW) {
                event.setState(EventState.CANCELED);
            }
        }
        
        updateEventCommonFields(event, request.getTitle(), request.getAnnotation(), request.getDescription(),
                request.getCategory(), request.getLocation(), request.getPaid(), request.getParticipantLimit(),
                request.getRequestModeration());
        
        return toEventFullDtoWithStats(eventRepository.save(event));
    }

    @Override
    public List<ru.practicum.request.dto.ParticipationRequestDto> getEventRequests(Long userId, Long eventId) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        
        return requestRepository.findAllByEventId(eventId).stream()
                .map(this::mapToRequestDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId, EventRequestStatusUpdateRequest request) {
        checkUser(userId);
        Event event = eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        
        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            throw new ConflictException("Moderation is not required");
        }
        
        Long confirmedCount = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (event.getParticipantLimit() <= confirmedCount) {
             throw new ConflictException("The participant limit has been reached");
        }
        
        List<ParticipationRequest> requests = requestRepository.findAllByIdIn(request.getRequestIds());
        List<ru.practicum.request.dto.ParticipationRequestDto> confirmed = new ArrayList<>();
        List<ru.practicum.request.dto.ParticipationRequestDto> rejected = new ArrayList<>();
        
        for (ParticipationRequest req : requests) {
            if (req.getStatus() != RequestStatus.PENDING) {
                throw new ConflictException("Request must have status PENDING");
            }
            
            if (request.getStatus() == RequestStatus.CONFIRMED) {
                if (confirmedCount < event.getParticipantLimit()) {
                    req.setStatus(RequestStatus.CONFIRMED);
                    confirmed.add(mapToRequestDto(req));
                    confirmedCount++;
                } else {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(mapToRequestDto(req));
                }
            } else {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(mapToRequestDto(req));
            }
        }
        requestRepository.saveAll(requests);
        return new EventRequestStatusUpdateResult(confirmed, rejected);
    }
    
    private void updateEventCommonFields(Event event, String title, String annotation, String description,
                                         Long categoryId, LocationDto location, Boolean paid, Integer participantLimit,
                                         Boolean requestModeration) {
        if (title != null) event.setTitle(title);
        if (annotation != null) event.setAnnotation(annotation);
        if (description != null) event.setDescription(description);
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId)
                    .orElseThrow(() -> new NotFoundException("Category not found"));
            event.setCategory(category);
        }
        if (location != null) {
            event.setLocation(eventMapper.toLocation(location));
        }
        if (paid != null) event.setPaid(paid);
        if (participantLimit != null) event.setParticipantLimit(participantLimit);
        if (requestModeration != null) event.setRequestModeration(requestModeration);
    }

    private User checkUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    private LocalDateTime parseTime(String time) {
        return LocalDateTime.parse(time, FORMATTER);
    }
    
    private void sendStat(HttpServletRequest request) {
        EndpointHitDto hit = new EndpointHitDto();
        hit.setApp("ewm-main-service");
        hit.setUri(request.getRequestURI());
        hit.setIp(request.getRemoteAddr());
        hit.setTimestamp(LocalDateTime.now().format(FORMATTER));
        statClient.hit(hit);
    }

    private EventFullDto toEventFullDtoWithStats(Event event) {
        EventFullDto dto = eventMapper.toEventFullDto(event);
        enrichDto(dto.getId(), dto);
        return dto;
    }
    
    private EventShortDto toEventShortDtoWithStats(Event event) {
        EventShortDto dto = eventMapper.toEventShortDto(event);
        enrichDto(dto.getId(), dto);
        return dto;
    }
    
    private void enrichDto(Long eventId, Object dto) {
        try {
            List<ViewStatsDto> stats = statClient.getStat(LocalDateTime.now().minusYears(100), LocalDateTime.now().plusYears(100), 
                    List.of("/events/" + eventId), true);
            Long views = stats.isEmpty() ? 0L : stats.get(0).getHits();
            if (dto instanceof EventFullDto) ((EventFullDto) dto).setViews(views);
            else if (dto instanceof EventShortDto) ((EventShortDto) dto).setViews(views);
        } catch (Exception e) {
        }
        
        Long confirmed = requestRepository.countByEventIdAndStatus(eventId, RequestStatus.CONFIRMED);
        if (dto instanceof EventFullDto) ((EventFullDto) dto).setConfirmedRequests(confirmed);
        else if (dto instanceof EventShortDto) ((EventShortDto) dto).setConfirmedRequests(confirmed);
    }
    
    private ru.practicum.request.dto.ParticipationRequestDto mapToRequestDto(ParticipationRequest req) {
        return ru.practicum.request.dto.ParticipationRequestDto.builder()
            .id(req.getId())
            .created(req.getCreated())
            .event(req.getEventId().getId())
            .requester(req.getRequesterId().getId())
            .status(req.getStatus().toString())
            .build();
    }

    @Override
    public EventFullDto getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return toEventFullDtoWithStats(event);
    }
}
