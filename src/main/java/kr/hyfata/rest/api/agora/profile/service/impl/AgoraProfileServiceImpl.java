package kr.hyfata.rest.api.agora.profile.service.impl;

import kr.hyfata.rest.api.auth.dto.agora.AgoraProfileResponse;
import kr.hyfata.rest.api.auth.dto.agora.CreateAgoraProfileRequest;
import kr.hyfata.rest.api.auth.dto.agora.PublicAgoraProfileResponse;
import kr.hyfata.rest.api.auth.dto.agora.UpdateAgoraProfileRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.entity.agora.AgoraUserProfile;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.repository.agora.AgoraUserProfileRepository;
import kr.hyfata.rest.api.agora.profile.service.AgoraProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraProfileServiceImpl implements AgoraProfileService {

    private final UserRepository userRepository;
    private final AgoraUserProfileRepository agoraUserProfileRepository;

    @Override
    public AgoraProfileResponse getMyProfile(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return agoraUserProfileRepository.findById(user.getId())
                .map(AgoraProfileResponse::from)
                .orElse(null);
    }

    @Override
    @Transactional
    public AgoraProfileResponse createProfile(String userEmail, CreateAgoraProfileRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (agoraUserProfileRepository.existsById(user.getId())) {
            throw new IllegalStateException("Agora profile already exists");
        }

        if (agoraUserProfileRepository.existsByAgoraId(request.getAgoraId())) {
            throw new IllegalArgumentException("agoraId already taken");
        }

        AgoraUserProfile profile = AgoraUserProfile.builder()
                .user(user)
                .agoraId(request.getAgoraId())
                .displayName(request.getDisplayName())
                .profileImage(request.getProfileImage())
                .bio(request.getBio())
                .phone(request.getPhone())
                .birthday(request.getBirthday())
                .build();

        AgoraUserProfile saved = agoraUserProfileRepository.save(profile);
        return AgoraProfileResponse.from(saved);
    }

    @Override
    @Transactional
    public AgoraProfileResponse updateProfile(String userEmail, UpdateAgoraProfileRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AgoraUserProfile profile = agoraUserProfileRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Agora profile not found. Please create a profile first."));

        if (request.getAgoraId() != null) {
            String newAgoraId = request.getAgoraId();
            if (!newAgoraId.equals(profile.getAgoraId())) {
                if (agoraUserProfileRepository.existsByAgoraId(newAgoraId)) {
                    throw new IllegalArgumentException("agoraId already taken");
                }
                profile.setAgoraId(newAgoraId);
            }
        }
        if (request.getDisplayName() != null) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (request.getProfileImage() != null) {
            profile.setProfileImage(request.getProfileImage());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }
        if (request.getBirthday() != null) {
            profile.setBirthday(request.getBirthday());
        }

        return AgoraProfileResponse.from(profile);
    }

    @Override
    public PublicAgoraProfileResponse getUserProfile(String agoraId) {
        return agoraUserProfileRepository.findByAgoraId(agoraId)
                .map(PublicAgoraProfileResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found: " + agoraId));
    }

    @Override
    public Page<PublicAgoraProfileResponse> searchUsers(String keyword, Pageable pageable) {
        return agoraUserProfileRepository.findByAgoraIdContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(
                keyword, keyword, pageable
        ).map(PublicAgoraProfileResponse::from);
    }

    @Override
    @Transactional
    public AgoraProfileResponse updateProfileImage(String userEmail, String imageUrl) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        AgoraUserProfile profile = agoraUserProfileRepository.findById(user.getId())
                .orElseThrow(() -> new IllegalStateException("Agora profile not found. Please create a profile first."));

        profile.setProfileImage(imageUrl);
        agoraUserProfileRepository.save(profile);

        return AgoraProfileResponse.from(profile);
    }

    @Override
    public boolean checkAgoraIdExists(String agoraId) {
        return agoraUserProfileRepository.existsByAgoraId(agoraId);
    }
}
