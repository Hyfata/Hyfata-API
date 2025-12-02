package kr.hyfata.rest.api.dto.agora;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgoraProfileRequest {

    @Size(min = 1, max = 100, message = "displayName must be between 1 and 100 characters")
    private String displayName;

    private String profileImage;
    private String bio;
    private String phone;
    private LocalDate birthday;
}
