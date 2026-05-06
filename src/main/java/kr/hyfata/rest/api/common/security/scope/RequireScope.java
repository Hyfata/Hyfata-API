package kr.hyfata.rest.api.common.security.scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 접근에 필요한 OAuth Scope를 지정하는 어노테이션
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireScope {
    /**
     * 필요한 scope 목록 (OR 조건 - 하나라도 만족하면 접근 허용)
     */
    String[] value() default {};

    /**
     * 필요한 scope 목록 (AND 조건 - 모두 만족해야 접근 허용)
     */
    String[] all() default {};
}
