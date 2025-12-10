package kr.hyfata.rest.api.service.agora.impl;

import kr.hyfata.rest.api.dto.agora.team.TeamProfileResponse;
import kr.hyfata.rest.api.dto.agora.team.CreateTeamProfileRequest;
import kr.hyfata.rest.api.entity.User;
import kr.hyfata.rest.api.entity.agora.TeamProfile;
import kr.hyfata.rest.api.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.TeamProfileRepository;
import kr.hyfata.rest.api.service.agora.AgoraTeamProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraTeamProfileServiceImpl implements AgoraTeamProfileService {

    private final UserRepository userRepository;
    private final TeamProfileRepository teamProfileRepository;

    @Override
    public TeamProfileResponse getMyTeamProfile(String userEmail) {
        User user = findUserByEmail(userEmail);

        TeamProfile profile = teamProfileRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Team profile not found"));

        return TeamProfileResponse.from(profile);
    }

    @Override
    @Transactional
    public TeamProfileResponse createTeamProfile(String userEmail, CreateTeamProfileRequest request) {
        User user = findUserByEmail(userEmail);

        // Check if profile already exists
        if (teamProfileRepository.existsById(user.getId())) {
            throw new IllegalStateException("Team profile already exists");
        }

        TeamProfile profile = TeamProfile.builder()
                .user(user)
                .displayName(request.getDisplayName())
                .profileImage(request.getProfileImage())
                .build();

        TeamProfile savedProfile = teamProfileRepository.save(profile);
        return TeamProfileResponse.from(savedProfile);
    }

    @Override
    @Transactional
    public TeamProfileResponse updateTeamProfile(String userEmail, String displayName, String profileImage, String bio) {
        User user = findUserByEmail(userEmail);

        TeamProfile profile = teamProfileRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Team profile not found"));

        if (displayName != null && !displayName.isEmpty()) {
            profile.setDisplayName(displayName);
        }

        if (profileImage != null) {
            profile.setProfileImage(profileImage);
        }

        if (bio != null) {
            profile.setBio(bio);
        }

        TeamProfile updated = teamProfileRepository.save(profile);
        return TeamProfileResponse.from(updated);
    }

    @Override
    @Transactional
    public TeamProfileResponse updateTeamProfileImage(String userEmail, String profileImage) {
        User user = findUserByEmail(userEmail);

        TeamProfile profile = teamProfileRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Team profile not found"));

        if (profileImage != null) {
            profile.setProfileImage(profileImage);
        }

        TeamProfile updated = teamProfileRepository.save(profile);
        return TeamProfileResponse.from(updated);
    }

    @Override
    public TeamProfileResponse getUserTeamProfile(Long userId) {
        TeamProfile profile = teamProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Team profile not found"));

        return TeamProfileResponse.from(profile);
    }

    @Override
    public boolean hasTeamProfile(String userEmail) {
        User user = findUserByEmail(userEmail);
        return teamProfileRepository.existsById(user.getId());
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
