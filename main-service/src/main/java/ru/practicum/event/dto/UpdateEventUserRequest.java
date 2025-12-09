package ru.practicum.event.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateEventUserRequest {
    @Size(min = 3, max = 120)
    private String title;
    
    @Size(min = 20, max = 2000)
    private String annotation;
    
    @Size(min = 20, max = 7000)
    private String description;
    
    private Long category;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventDate;
    
    private LocationDto location;
    
    private Boolean paid;
    
    @PositiveOrZero
    private Integer participantLimit;
    
    private Boolean requestModeration;
    
    private StateAction stateAction;
    
    public enum StateAction {
        SEND_TO_REVIEW,
        CANCEL_REVIEW
    }
}

