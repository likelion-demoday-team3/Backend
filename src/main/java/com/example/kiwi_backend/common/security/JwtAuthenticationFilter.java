package com.example.kiwi_backend.common.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 요청 헤더의 로그인 토큰을 검증해서 AuthUser를 principal로 넣는다.
 *
 * 토큰이 잘못돼도 여기서 바로 401로 끊지 않는다.
 * 로그인 API처럼 열린 경로는 잘못된 토큰이 붙어 와도 통과해야 하기 때문.
 * 실패 이유만 요청에 남겨두고, 인증이 필요한 경로라면 EntryPoint가 그 이유로 401을 응답한다.
 *
 * @Component를 붙이지 않는다. 붙이면 Spring Boot가 일반 서블릿 필터로도 등록해서 두 번 실행된다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_ERROR_ATTRIBUTE = "tokenError";

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            try {
                AuthUser authUser = jwtProvider.parseAccessToken(token);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        authUser,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + authUser.role()))
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidTokenException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, e.getReason());
            }
        }

        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        return header.substring(PREFIX.length()).trim();
    }
}
