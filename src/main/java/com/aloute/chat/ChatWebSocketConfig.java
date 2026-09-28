package com.aloute.chat;

import com.aloute.security.AlouteUserPrincipal;
import com.aloute.security.CookieService;
import com.aloute.security.JwtService;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Kênh realtime cho chat, chỉ để SERVER ĐẨY tin nhắn mới (client gửi tin qua {@code POST /messages/{id}/send}
 * bình thường, không qua STOMP) — nên chỉ cần bật broker {@code /topic}, không cần prefix {@code /app}.
 * Xác thực bằng chính cookie JWT đang dùng cho các trang khác (xem {@link JwtHandshakeInterceptor}); việc
 * ai được SUBSCRIBE hội thoại nào do {@link ChatChannelInterceptor} kiểm tra.
 */
@Configuration
@EnableWebSocketMessageBroker
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final CookieService cookies;
    private final ChatChannelInterceptor channelInterceptor;

    public ChatWebSocketConfig(JwtService jwtService, CookieService cookies, ChatChannelInterceptor channelInterceptor) {
        this.jwtService = jwtService;
        this.cookies = cookies;
        this.channelInterceptor = channelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(new UserHandshakeHandler())
                .addInterceptors(new JwtHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(channelInterceptor);
    }

    /** Đọc cookie {@value CookieService#ACCESS_COOKIE} lúc bắt tay, không cần token trong URL (SockJS gửi kèm cookie sẵn). */
    private class JwtHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, org.springframework.http.server.ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                return false;
            }
            Optional<AlouteUserPrincipal> principal = cookies.read(servletRequest.getServletRequest(), CookieService.ACCESS_COOKIE)
                    .flatMap(jwtService::parse);
            principal.ifPresent(p -> attributes.put("principal", p));
            return principal.isPresent();
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, org.springframework.http.server.ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }

    /** Gắn {@link AlouteUserPrincipal} (đọc được ở {@link JwtHandshakeInterceptor}) làm Principal của phiên STOMP. */
    private static class UserHandshakeHandler extends DefaultHandshakeHandler {
        @Override
        protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
            return (Principal) attributes.get("principal");
        }
    }
}
