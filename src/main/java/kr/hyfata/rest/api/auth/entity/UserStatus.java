package kr.hyfata.rest.api.auth.entity;

public enum UserStatus {
    ACTIVE,      // 정상
    DEACTIVATED, // 비활성화 (복구 가능)
    DELETED      // 삭제됨 (복구 불가, 유예 기간 후 실제 삭제)
}
