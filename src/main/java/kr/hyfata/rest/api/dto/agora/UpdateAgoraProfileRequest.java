package kr.hyfata.rest.api.dto.agora;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgoraProfileRequest {

    @Size(min = 3, max = 50, message = "agoraId must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "agoraId can only contain letters, numbers, and underscores")
    private String agoraId;

    @Size(min = 1, max = 100, message = "displayName must be between 1 and 100 characters")
    private String displayName;

    private String profileImage;
    private String bio;
    private String phone;
    private LocalDate birthday;
}
