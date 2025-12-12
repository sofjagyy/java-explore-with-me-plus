package ru.practicum.request.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.practicum.request.model.ParticipationRequest;

import java.util.List;
import java.util.Optional;

public interface ParticipationRequestRepository extends JpaRepository<ParticipationRequest, Long> {

    Optional<ParticipationRequest> findByEventIdAndRequesterId(Long eventId, Long userId);

    List<ParticipationRequest> findAllByEventIdAndStatus(Long eventId, ru.practicum.request.enums.RequestStatus status);

    List<ParticipationRequest> findAllByRequesterId(Long requesterId);
    
    Long countByEventIdAndStatus(Long eventId, ru.practicum.request.enums.RequestStatus status);
    
    List<ParticipationRequest> findAllByEventId(Long eventId);
    
    List<ParticipationRequest> findAllByIdIn(List<Long> ids);
}
