package kr.hyfata.rest.api.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Spring Session + Redis 설정
 * OAuth 로그인 화면의 서버사이드 세션 저장에 사용
 */
@Configuration
@EnableRedisHttpSession
public class SessionConfig {
}
