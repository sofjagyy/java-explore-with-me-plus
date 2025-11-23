package ru.practicum;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.practicum.dto.ViewStatsDto;

import java.time.LocalDateTime;
import java.util.List;

public interface EndpointHitRepository extends JpaRepository<EndpointHit, Long> {

    @Query("SELECT new ru.practicum.dto.ViewStatsDto(" +
           "   h.app, " +
           "   h.uri, " +
           "   CASE WHEN :unique = true THEN COUNT(DISTINCT h.ip) ELSE COUNT(h.ip) END " +
           ") " +
           "FROM EndpointHit h " +
           "WHERE h.timestamp BETWEEN :start AND :end " +
           "AND ((:uris) IS NULL OR h.uri IN (:uris)) " +
           "GROUP BY h.app, h.uri " +
           "ORDER BY CASE WHEN :unique = true THEN COUNT(DISTINCT h.ip) ELSE COUNT(h.ip) END DESC")
    List<ViewStatsDto> findStats(@Param("start") LocalDateTime start,
                                 @Param("end") LocalDateTime end,
                                 @Param("uris") List<String> uris,
                                 @Param("unique") Boolean unique);
}

