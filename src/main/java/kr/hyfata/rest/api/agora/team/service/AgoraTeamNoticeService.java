package kr.hyfata.rest.api.agora.team.service;

import kr.hyfata.rest.api.agora.team.dto.NoticeResponse;
import kr.hyfata.rest.api.agora.team.dto.CreateNoticeRequest;
import kr.hyfata.rest.api.agora.team.dto.UpdateNoticeRequest;

import java.util.List;

public interface AgoraTeamNoticeService {

    List<NoticeResponse> getNoticeList(String userEmail, Long teamId);

    NoticeResponse getNoticeDetail(String userEmail, Long teamId, Long noticeId);

    NoticeResponse createNotice(String userEmail, Long teamId, CreateNoticeRequest request);

    NoticeResponse updateNotice(String userEmail, Long teamId, Long noticeId, UpdateNoticeRequest request);

    String deleteNotice(String userEmail, Long teamId, Long noticeId);
}
