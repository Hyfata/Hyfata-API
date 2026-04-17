package kr.hyfata.rest.api.agora.team.service.impl;

import kr.hyfata.rest.api.auth.dto.agora.team.EventResponse;
import kr.hyfata.rest.api.auth.dto.agora.team.CreateEventRequest;
import kr.hyfata.rest.api.auth.dto.agora.team.UpdateEventRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.agora.team.entity.Team
import kr.hyfata.rest.api.entity.agora.Event;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.agora.team.repository.TeamRepository;
import kr.hyfata.rest.api.repository.agora.EventRepository;
import kr.hyfata.rest.api.agora.team.repository.TeamMemberRepository;
import kr.hyfata.rest.api.agora.team.service.AgoraTeamEventService;
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
public class AgoraTeamEventServiceImpl implements AgoraTeamEventService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final EventRepository eventRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public List<EventResponse> getEventList(String userEmail, Long teamId) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        List<Event> events = eventRepository.findByTeam_IdOrderByStartTimeAsc(teamId);
        return events.stream()
                .map(EventResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public EventResponse getEventDetail(String userEmail, Long teamId, Long eventId) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        Event event = findEventById(eventId);

        // 해당 팀의 일정인지 확인
        if (!event.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 일정이 아닙니다");
        }

        return EventResponse.from(event);
    }

    @Override
    @Transactional
    public EventResponse createEvent(String userEmail, Long teamId, CreateEventRequest request) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        // 관리자인지 확인 (팀 생성자는 관리자)
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("일정을 작성할 권한이 없습니다");
        }

        Event event = Event.builder()
                .team(team)
                .createdBy(user)
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isAllDay(request.getIsAllDay() != null ? request.getIsAllDay() : false)
                .build();

        Event savedEvent = eventRepository.save(event);
        return EventResponse.from(savedEvent);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(String userEmail, Long teamId, Long eventId, UpdateEventRequest request) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        // 관리자인지 확인
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("일정을 수정할 권한이 없습니다");
        }

        Event event = findEventById(eventId);

        // 해당 팀의 일정인지 확인
        if (!event.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 일정이 아닙니다");
        }

        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            event.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            event.setDescription(request.getDescription());
        }

        if (request.getLocation() != null) {
            event.setLocation(request.getLocation());
        }

        if (request.getStartTime() != null) {
            event.setStartTime(request.getStartTime());
        }

        if (request.getEndTime() != null) {
            event.setEndTime(request.getEndTime());
        }

        if (request.getIsAllDay() != null) {
            event.setIsAllDay(request.getIsAllDay());
        }

        Event updated = eventRepository.save(event);
        return EventResponse.from(updated);
    }

    @Override
    @Transactional
    public String deleteEvent(String userEmail, Long teamId, Long eventId) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        // 관리자인지 확인
        if (!team.getCreatedBy().getId().equals(user.getId())) {
            throw new IllegalStateException("일정을 삭제할 권한이 없습니다");
        }

        Event event = findEventById(eventId);

        // 해당 팀의 일정인지 확인
        if (!event.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 일정이 아닙니다");
        }

        eventRepository.deleteById(eventId);
        return "일정이 삭제되었습니다";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private Team findTeamById(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다"));
    }

    private Event findEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다"));
    }

    private boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId);
    }
}
