package com.instaclone.config;

import com.instaclone.common.SecurityUtils;
import java.security.Principal;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Real auth for the WebSocket path — the HTTP handshake itself is permitAll (browsers can't
 * attach an Authorization header to a WebSocket upgrade request), so the actual gate is here: the
 * STOMP CONNECT frame carries its own Authorization header, validated with the same JwtDecoder
 * bean the REST API uses. A missing/invalid token rejects the CONNECT before a session exists.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    public StompAuthChannelInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                throw new BadCredentialsException("Missing bearer token on STOMP CONNECT");
            }
            try {
                Long userId = SecurityUtils.currentUserId(jwtDecoder.decode(authHeader.substring(7)));
                Principal principal = () -> String.valueOf(userId);
                accessor.setUser(principal);
            } catch (JwtException e) {
                throw new BadCredentialsException("Invalid bearer token on STOMP CONNECT", e);
            }
        }
        return message;
    }
}
