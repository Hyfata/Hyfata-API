package kr.hyfata.rest.api.agora.team.service;

import kr.hyfata.rest.api.agora.team.dto.TodoResponse;
import kr.hyfata.rest.api.agora.team.dto.CreateTodoRequest;
import kr.hyfata.rest.api.agora.team.dto.UpdateTodoRequest;

import java.util.List;

public interface AgoraTeamTodoService {

    List<TodoResponse> getTodoList(String userEmail, Long teamId);

    TodoResponse getTodoDetail(String userEmail, Long teamId, Long todoId);

    TodoResponse createTodo(String userEmail, Long teamId, CreateTodoRequest request);

    TodoResponse updateTodo(String userEmail, Long teamId, Long todoId, UpdateTodoRequest request);

    TodoResponse completeTodo(String userEmail, Long teamId, Long todoId);

    String deleteTodo(String userEmail, Long teamId, Long todoId);
}
