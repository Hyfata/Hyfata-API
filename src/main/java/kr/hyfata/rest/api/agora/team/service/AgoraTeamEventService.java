package kr.hyfata.rest.api.agora.team.service;

import kr.hyfata.rest.api.auth.dto.agora.team.EventResponse;
import kr.hyfata.rest.api.auth.dto.agora.team.CreateEventRequest;
import kr.hyfata.rest.api.auth.dto.agora.team.UpdateEventRequest;

import java.util.List;

public interface AgoraTeamEventService {

    List<EventResponse> getEventList(String userEmail, Long teamId);

    EventResponse getEventDetail(String userEmail, Long teamId, Long eventId);

    EventResponse createEvent(String userEmail, Long teamId, CreateEventRequest request);

    EventResponse updateEvent(String userEmail, Long teamId, Long eventId, UpdateEventRequest request);

    String deleteEvent(String userEmail, Long teamId, Long eventId);
}
