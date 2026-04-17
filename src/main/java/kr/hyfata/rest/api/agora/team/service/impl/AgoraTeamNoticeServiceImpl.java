package kr.hyfata.rest.api.agora.team.service.impl;

import kr.hyfata.rest.api.agora.team.dto.NoticeResponse;
import kr.hyfata.rest.api.agora.team.dto.CreateNoticeRequest;
import kr.hyfata.rest.api.agora.team.dto.UpdateNoticeRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.agora.team.entity.Team;
import kr.hyfata.rest.api.agora.team.entity.Notice;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.agora.team.repository.TeamRepository;
import kr.hyfata.rest.api.agora.team.repository.NoticeRepository;
import kr.hyfata.rest.api.agora.team.repository.TeamMemberRepository;
import kr.hyfata.rest.api.agora.team.service.AgoraTeamNoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraTeamNoticeServiceImpl implements AgoraTeamNoticeService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final NoticeRepository noticeRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public List<NoticeResponse> getNoticeList(String userEmail, Long teamId) {
        User user = findUserByEmail(userEmail);
        findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        List<Notice> notices = noticeRepository.findByTeam_IdOrderByCreatedAtDesc(teamId);
        return notices.stream()
                .map(NoticeResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public NoticeResponse getNoticeDetail(String userEmail, Long teamId, Long noticeId) {
        User user = findUserByEmail(userEmail);
        findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        Notice notice = findNoticeById(noticeId);

        // 해당 팀의 공지인지 확인
        if (!notice.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 공지가 아닙니다");
        }

        return NoticeResponse.from(notice);
    }

    @Override
    @Transactional
    public NoticeResponse createNotice(String userEmail, Long teamId, CreateNoticeRequest request) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        // 관리자인지 확인 (팀 생성자는 관리자)
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("공지를 작성할 권한이 없습니다");
        }

        Notice notice = Notice.builder()
                .team(team)
                .author(user)
                .title(request.getTitle())
                .content(request.getContent())
                .isPinned(request.getIsPinned() != null ? request.getIsPinned() : false)
                .build();

        Notice savedNotice = noticeRepository.save(notice);
        return NoticeResponse.from(savedNotice);
    }

    @Override
    @Transactional
    public NoticeResponse updateNotice(String userEmail, Long teamId, Long noticeId, UpdateNoticeRequest request) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        // 관리자인지 확인
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("공지를 수정할 권한이 없습니다");
        }

        Notice notice = findNoticeById(noticeId);

        // 해당 팀의 공지인지 확인
        if (!notice.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 공지가 아닙니다");
        }

        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            notice.setTitle(request.getTitle());
        }

        if (request.getContent() != null && !request.getContent().isEmpty()) {
            notice.setContent(request.getContent());
        }

        if (request.getIsPinned() != null) {
            notice.setIsPinned(request.getIsPinned());
        }

        Notice updated = noticeRepository.save(notice);
        return NoticeResponse.from(updated);
    }

    @Override
    @Transactional
    public String deleteNotice(String userEmail, Long teamId, Long noticeId) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        // 관리자인지 확인
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("공지를 삭제할 권한이 없습니다");
        }

        Notice notice = findNoticeById(noticeId);

        // 해당 팀의 공지인지 확인
        if (!notice.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 공지가 아닙니다");
        }

        noticeRepository.deleteById(noticeId);
        return "공지가 삭제되었습니다";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private Team findTeamById(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다"));
    }

    private Notice findNoticeById(Long noticeId) {
        return noticeRepository.findById(noticeId)
                .orElseThrow(() -> new IllegalArgumentException("공지를 찾을 수 없습니다"));
    }

    private boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId);
    }
}
