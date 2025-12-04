package kr.hyfata.rest.api.config;

import kr.hyfata.rest.api.security.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // TaskScheduler 생성 (heartbeat용)
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("ws-heartbeat-");
        taskScheduler.initialize();

        // 메시지 브로커 설정
        config.enableSimpleBroker("/topic", "/user")
                .setHeartbeatValue(new long[]{25000, 25000})
                .setTaskScheduler(taskScheduler);

        // 클라이언트에서 서버로 보낼 메시지의 접두어
        config.setApplicationDestinationPrefixes("/app");

        // 사용자별 메시지 큐 프리픽스
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket 핸드셰이크 엔드포인트
        // 클라이언트는 /ws/agora/chat으로 WebSocket 연결
        registry.addEndpoint("/ws/agora/chat")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOrigins("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // JWT 검증 인터셉터 추가
        registration.interceptors(jwtChannelInterceptor);
    }
}
