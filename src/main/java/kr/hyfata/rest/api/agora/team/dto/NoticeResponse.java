package kr.hyfata.rest.api.dto.agora.team;

import kr.hyfata.rest.api.entity.agora.Notice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoticeResponse {

    private Long noticeId;

    private Long teamId;

    private String authorEmail;

    private String title;

    private String content;

    private Boolean isPinned;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public static NoticeResponse from(Notice notice) {
        return NoticeResponse.builder()
                .noticeId(notice.getId())
                .teamId(notice.getTeam().getId())
                .authorEmail(notice.getAuthor().getEmail())
                .title(notice.getTitle())
                .content(notice.getContent())
                .isPinned(notice.getIsPinned())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}
