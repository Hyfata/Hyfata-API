package kr.hyfata.rest.api.auth.service.impl;

import kr.hyfata.rest.api.auth.dto.account.*;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.entity.UserStatus;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final String RESTORE_CONFIRM_TEXT = "계정을 삭제합니다";
    private static final int RESTORE_DAYS = 30;

    @Override
    public String changePassword(String email, ChangePasswordRequest request) {
        User user = findUserByEmail(email);

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다");
        }

        // 새 비밀번호 확인
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다");
        }

        // 비밀번호 변경
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return "비밀번호가 변경되었습니다";
    }

    @Override
    public String deactivateAccount(String email, DeactivateAccountRequest request) {
        User user = findUserByEmail(email);

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        // 계정 비활성화
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeactivatedAt(LocalDateTime.now());
        user.setDeactivationReason(request.getReason());
        userRepository.save(user);

        return "계정이 비활성화되었습니다";
    }

    @Override
    public String deleteAccount(String email, DeleteAccountRequest request) {
        User user = findUserByEmail(email);

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        // 확인 문구 검증
        if (!RESTORE_CONFIRM_TEXT.equals(request.getConfirmText())) {
            throw new IllegalArgumentException("확인 문구가 일치하지 않습니다");
        }

        // 계정 삭제 (소프트 삭제)
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        return "계정이 삭제되었습니다. 30일 후 완전히 삭제됩니다";
    }

    @Override
    public String restoreAccount(RestoreAccountRequest request) {
        User user = findUserByEmail(request.getEmail());

        // 비활성화 상태 확인
        if (user.getStatus() != UserStatus.DEACTIVATED) {
            throw new IllegalArgumentException("비활성화된 계정이 아닙니다");
        }

        // 복구 가능 기간 확인 (30일)
        if (user.getDeactivatedAt().plusDays(RESTORE_DAYS).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("복구 기한이 만료되었습니다");
        }

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        // 계정 복구
        user.setStatus(UserStatus.ACTIVE);
        user.setDeactivatedAt(null);
        user.setDeactivationReason(null);
        userRepository.save(user);

        return "계정이 복구되었습니다. 다시 로그인해주세요";
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }
}
