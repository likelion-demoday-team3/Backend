package com.example.kiwi_backend.common.security;

import tools.jackson.databind.ObjectMapper;
import com.example.kiwi_backend.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증이 필요한 경로에 로그인 없이 들어왔을 때의 401 응답.
 * 필터가 남긴 실패 이유로 코드를 나눠서, 프론트가 만료면 로그인 화면으로 보낼 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE);

        String code;
        String message;
        if (reason == InvalidTokenException.Reason.EXPIRED) {
            code = "TOKEN_EXPIRED";
            message = "로그인이 만료됐어요. 다시 로그인해주세요.";
        } else if (reason == InvalidTokenException.Reason.INVALID) {
            code = "INVALID_TOKEN";
            message = "유효하지 않은 토큰입니다.";
        } else {
            code = "UNAUTHORIZED";
            message = "로그인이 필요합니다.";
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(code, message));
    }
}