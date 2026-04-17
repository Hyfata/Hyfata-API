package kr.hyfata.rest.api.agora.team.service;

import kr.hyfata.rest.api.agora.team.dto.EventResponse;
import kr.hyfata.rest.api.agora.team.dto.CreateEventRequest;
import kr.hyfata.rest.api.agora.team.dto.UpdateEventRequest;

import java.util.List;

public interface AgoraTeamEventService {

    List<EventResponse> getEventList(String userEmail, Long teamId);

    EventResponse getEventDetail(String userEmail, Long teamId, Long eventId);

    EventResponse createEvent(String userEmail, Long teamId, CreateEventRequest request);

    EventResponse updateEvent(String userEmail, Long teamId, Long eventId, UpdateEventRequest request);

    String deleteEvent(String userEmail, Long teamId, Long eventId);
}
