package kr.hyfata.rest.api.agora.chat.dto;

import lombok.Data;

@Data
public class CreateChatFolderRequest {
    private String name;
    private Integer orderIndex;
}
