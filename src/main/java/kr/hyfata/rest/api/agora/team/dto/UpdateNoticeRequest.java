package kr.hyfata.rest.api.agora.team.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNoticeRequest {

    private String title;

    private String content;

    private Boolean isPinned;
}
