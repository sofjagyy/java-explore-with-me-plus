package ru.practicum.event.service;

import jakarta.servlet.http.HttpServletRequest;
import ru.practicum.event.dto.*;

import java.util.List;

public interface EventService {
    List<EventFullDto> getEventsAdmin(EventAdminFilterParams params);

    EventFullDto updateEventAdmin(Long eventId, UpdateEventAdminRequest request);

    List<EventShortDto> getEventsPublic(EventPublicFilterParams params);

    EventFullDto getEventPublic(Long id, HttpServletRequest request);

    List<EventShortDto> getEventsUser(Long userId, int from, int size);

    EventFullDto createEventUser(Long userId, NewEventDto newEventDto);

    EventFullDto getEventUser(Long userId, Long eventId);

    EventFullDto updateEventUser(Long userId, Long eventId, UpdateEventUserRequest request);

    List<ru.practicum.request.dto.ParticipationRequestDto> getEventRequests(Long userId, Long eventId);

    EventRequestStatusUpdateResult changeRequestStatus(Long userId, Long eventId, EventRequestStatusUpdateRequest request);

    EventFullDto getEventById(Long eventId);
}
