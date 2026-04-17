package kr.hyfata.rest.api.dto.agora.team;

import kr.hyfata.rest.api.entity.agora.Event;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponse {

    private Long eventId;

    private Long teamId;

    private String createdByEmail;

    private String title;

    private String description;

    private String location;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Boolean isAllDay;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static EventResponse from(Event event) {
        return EventResponse.builder()
                .eventId(event.getId())
                .teamId(event.getTeam().getId())
                .createdByEmail(event.getCreatedBy().getEmail())
                .title(event.getTitle())
                .description(event.getDescription())
                .location(event.getLocation())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .isAllDay(event.getIsAllDay())
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
