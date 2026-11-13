package com.suwontrip.app.auth.dto;

/**
 * 세션 조회·로그인·로그아웃에서 공통으로 반환하는 응답
 * 인증 상태와 CSRF 토큰을 함께 보내 클라이언트가 다음 변경 요청을 준비할 수 있게함
 *
 * @param authenticated
 * @param user
 * @param csrfToken
 */
public record SessionResponse(boolean authenticated, User user, String csrfToken) {

    /**
     * 응답에 공개하는 사용자 정보만 담음.
     *
     * @param id 설정에서 주입한 고정 사용자 ID다. 화면 표시 이름과 구분한다.
     * @param displayName 화면에 보여 줄 사용자 이름이다.
     */
    public record User(String id, String displayName) { }
}
