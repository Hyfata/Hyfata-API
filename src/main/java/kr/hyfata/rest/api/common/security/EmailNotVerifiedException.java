package kr.hyfata.rest.api.common.security;

import org.springframework.security.core.AuthenticationException;

/**
 * 이메일 미인증 계정으로 로그인 시도 시 발생하는 예외
 * 로그인 페이지에서 구분된 에러 메시지를 표시하기 위해 사용
 */
public class EmailNotVerifiedException extends AuthenticationException {

    public EmailNotVerifiedException(String msg) {
        super(msg);
    }
}
