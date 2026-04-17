package kr.hyfata.rest.api.agora.team.service.impl;

import kr.hyfata.rest.api.agora.team.dto.TodoResponse;
import kr.hyfata.rest.api.agora.team.dto.CreateTodoRequest;
import kr.hyfata.rest.api.agora.team.dto.UpdateTodoRequest;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.agora.team.entity.Team;
import kr.hyfata.rest.api.agora.team.entity.Todo;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.agora.team.repository.TeamRepository;
import kr.hyfata.rest.api.agora.team.repository.TodoRepository;
import kr.hyfata.rest.api.agora.team.repository.TeamMemberRepository;
import kr.hyfata.rest.api.agora.team.service.AgoraTeamTodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AgoraTeamTodoServiceImpl implements AgoraTeamTodoService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TodoRepository todoRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public List<TodoResponse> getTodoList(String userEmail, Long teamId) {
        User user = findUserByEmail(userEmail);
        findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        List<Todo> todos = todoRepository.findByTeam_IdOrderByDueDateAscCreatedAtDesc(teamId);
        return todos.stream()
                .map(TodoResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public TodoResponse getTodoDetail(String userEmail, Long teamId, Long todoId) {
        User user = findUserByEmail(userEmail);
        findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        Todo todo = findTodoById(todoId);

        // 해당 팀의 할일인지 확인
        if (!todo.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 할일이 아닙니다");
        }

        return TodoResponse.from(todo);
    }

    @Override
    @Transactional
    public TodoResponse createTodo(String userEmail, Long teamId, CreateTodoRequest request) {
        User user = findUserByEmail(userEmail);
        Team team = findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        // 할당 대상 확인 (없으면 null)
        User assignedTo = null;
        if (request.getAssignedToId() != null) {
            assignedTo = findUserById(request.getAssignedToId());
            if (!isTeamMember(teamId, assignedTo.getId())) {
                throw new IllegalStateException("할당 대상이 팀의 멤버가 아닙니다");
            }
        }

        // Priority 파싱
        Todo.Priority priority = request.getPriority() != null
                ? Todo.Priority.valueOf(request.getPriority().toUpperCase())
                : Todo.Priority.MEDIUM;

        Todo todo = Todo.builder()
                .team(team)
                .createdBy(user)
                .assignedTo(assignedTo)
                .title(request.getTitle())
                .description(request.getDescription())
                .priority(priority)
                .dueDate(request.getDueDate())
                .status(Todo.Status.TODO)
                .build();

        Todo savedTodo = todoRepository.save(todo);
        return TodoResponse.from(savedTodo);
    }

    @Override
    @Transactional
    public TodoResponse updateTodo(String userEmail, Long teamId, Long todoId, UpdateTodoRequest request) {
        User user = findUserByEmail(userEmail);
        findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        Todo todo = findTodoById(todoId);

        // 해당 팀의 할일인지 확인
        if (!todo.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 할일이 아닙니다");
        }

        if (request.getTitle() != null && !request.getTitle().isEmpty()) {
            todo.setTitle(request.getTitle());
        }

        if (request.getDescription() != null) {
            todo.setDescription(request.getDescription());
        }

        // 할당 대상 변경
        if (request.getAssignedToId() != null) {
            User assignedTo = findUserById(request.getAssignedToId());
            if (!isTeamMember(teamId, assignedTo.getId())) {
                throw new IllegalStateException("할당 대상이 팀의 멤버가 아닙니다");
            }
            todo.setAssignedTo(assignedTo);
        }

        if (request.getPriority() != null) {
            todo.setPriority(Todo.Priority.valueOf(request.getPriority().toUpperCase()));
        }

        if (request.getDueDate() != null) {
            todo.setDueDate(request.getDueDate());
        }

        Todo updated = todoRepository.save(todo);
        return TodoResponse.from(updated);
    }

    @Override
    @Transactional
    public TodoResponse completeTodo(String userEmail, Long teamId, Long todoId) {
        User user = findUserByEmail(userEmail);
        findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        Todo todo = findTodoById(todoId);

        // 해당 팀의 할일인지 확인
        if (!todo.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 할일이 아닙니다");
        }

        todo.setStatus(Todo.Status.DONE);
        todo.setCompletedAt(LocalDateTime.now());

        Todo updated = todoRepository.save(todo);
        return TodoResponse.from(updated);
    }

    @Override
    @Transactional
    public String deleteTodo(String userEmail, Long teamId, Long todoId) {
        User user = findUserByEmail(userEmail);
        findTeamById(teamId);

        // 팀 멤버 확인
        if (!isTeamMember(teamId, user.getId())) {
            throw new IllegalStateException("팀의 멤버가 아닙니다");
        }

        Todo todo = findTodoById(todoId);

        // 해당 팀의 할일인지 확인
        if (!todo.getTeam().getId().equals(teamId)) {
            throw new IllegalStateException("해당 팀의 할일이 아닙니다");
        }

        todoRepository.deleteById(todoId);
        return "할일이 삭제되었습니다";
    }

    private User findUserByEmail(String userEmail) {
        return userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }

    private Team findTeamById(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("팀을 찾을 수 없습니다"));
    }

    private Todo findTodoById(Long todoId) {
        return todoRepository.findById(todoId)
                .orElseThrow(() -> new IllegalArgumentException("할일을 찾을 수 없습니다"));
    }

    private boolean isTeamMember(Long teamId, Long userId) {
        return teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId);
    }
}
