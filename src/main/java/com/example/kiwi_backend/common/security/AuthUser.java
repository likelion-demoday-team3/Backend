package com.example.kiwi_backend.common.security;

/**
 * 로그인한 사용자. JWT 필터가 토큰을 검증한 뒤 SecurityContext의 principal로 넣는다.
 * 컨트롤러에서는 @AuthenticationPrincipal AuthUser authUser 로 받는다.
 */
public record AuthUser(Long memberId, String role) {
}
