package ru.practicum;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.lang.NonNull;

public interface StatClient {
    void hit(@NonNull EndpointHitDto paramHitDto);
    
    List<ViewStatsDto> getStat(LocalDateTime start, LocalDateTime end, List<String> uris, Boolean unique);
}

