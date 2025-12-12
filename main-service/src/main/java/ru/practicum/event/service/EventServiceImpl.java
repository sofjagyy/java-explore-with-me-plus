package ru.practicum.event.service;

import com.querydsl.core.BooleanBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import ru.practicum.request.mapper.ParticipationRequestMapper;
import ru.practicum.request.model.ParticipationRequest;
import ru.practicum.request.repository.ConfirmedRequestView;
import ru.practicum.request.repository.ParticipationRequestRepository;
import ru.practicum.user.User;
import ru.practicum.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ParticipationRequestRepository requestRepository;
    private final StatClient statClient;
    private final EventMapper eventMapper;
    private final ParticipationRequestMapper requestMapper;

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

        return makeEventFullDtoList(events);
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
            // Сортировка по просмотрам будет выполнена после получения всех данных
        } else {
            sortOrder = Sort.by(Sort.Direction.ASC, "eventDate");
        }

        PageRequest pageRequest = PageRequest.of(from / size, size, sortOrder);
        List<Event> events = eventRepository.findAll(builder, pageRequest).getContent();

        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);
        List<EventShortDto> result = new ArrayList<>();

        for (Event event : events) {
            EventShortDto dto = eventMapper.toShortDto(event);
            dto.setViews(views.getOrDefault(event.getId(), 0L));
            dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));

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
        List<Event> events = eventRepository.findAllByInitiatorId(userId, pageRequest);
        return makeEventShortDtoList(events);
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

        Event event = eventMapper.toEntity(newEventDto);
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
        eventRepository.findByIdAndInitiatorId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        return requestRepository.findAllByEventId(eventId).stream()
                .map(requestMapper::toDto)
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
                    confirmed.add(requestMapper.toDto(req));
                    confirmedCount++;
                } else {
                    req.setStatus(RequestStatus.REJECTED);
                    rejected.add(requestMapper.toDto(req));
                }
            } else {
                req.setStatus(RequestStatus.REJECTED);
                rejected.add(requestMapper.toDto(req));
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

    private List<EventFullDto> makeEventFullDtoList(List<Event> events) {
        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventFullDto dto = eventMapper.toFullDto(event);
                    dto.setViews(views.getOrDefault(event.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private List<EventShortDto> makeEventShortDtoList(List<Event> events) {
        Map<Long, Long> views = getViews(events);
        Map<Long, Long> confirmedRequests = getConfirmedRequests(events);

        return events.stream()
                .map(event -> {
                    EventShortDto dto = eventMapper.toShortDto(event);
                    dto.setViews(views.getOrDefault(event.getId(), 0L));
                    dto.setConfirmedRequests(confirmedRequests.getOrDefault(event.getId(), 0L));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private EventFullDto toEventFullDtoWithStats(Event event) {
        return makeEventFullDtoList(List.of(event)).get(0);
    }

    private Map<Long, Long> getViews(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        Map<Long, Long> views = new HashMap<>();
        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .collect(Collectors.toList());

        try {
            List<ViewStatsDto> stats = statClient.getStat(LocalDateTime.now().minusYears(100), LocalDateTime.now().plusYears(100), uris, true);
            for (ViewStatsDto stat : stats) {
                String[] parts = stat.getUri().split("/");
                if (parts.length >= 3) {
                    Long eventId = Long.parseLong(parts[2]);
                    views.put(eventId, stat.getHits());
                }
            }
        } catch (Exception e) {
            log.error("Error getting stats", e);
        }
        return views;
    }

    private Map<Long, Long> getConfirmedRequests(List<Event> events) {
        if (events.isEmpty()) return Collections.emptyMap();

        List<Long> eventIds = events.stream().map(Event::getId).collect(Collectors.toList());
        List<ConfirmedRequestView> confirmedRequests = requestRepository.countByEventIdInAndStatus(eventIds, RequestStatus.CONFIRMED);

        return confirmedRequests.stream()
                .collect(Collectors.toMap(ConfirmedRequestView::getEventId, ConfirmedRequestView::getCount));
    }

    @Override
    public EventFullDto getEventById(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event with id=" + eventId + " was not found"));
        return toEventFullDtoWithStats(event);
    }
}
