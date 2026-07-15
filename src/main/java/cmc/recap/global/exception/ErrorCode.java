package cmc.recap.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통 (400)
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다"),

    // 공통 (404)
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다"),

    // 공통 (500)
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다"),

    // 인증 (401, 403)
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "로그인 아이디 또는 비밀번호가 올바르지 않습니다"),
    MEMBER_WITHDRAWN(HttpStatus.FORBIDDEN, "탈퇴한 회원입니다"),

    // 회원 (404, 409, 400)
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다"),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 로그인 아이디입니다"),
    MEMBER_ALREADY_WITHDRAWN(HttpStatus.BAD_REQUEST, "이미 탈퇴한 회원입니다"),

    // 소셜 로그인 (401, 404, 409)
    ALREADY_LINKED_OAUTH(HttpStatus.CONFLICT, "이미 소셜 계정이 연결된 유저입니다"),
    OAUTH_VERIFICATION_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인 토큰 검증에 실패했습니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다"),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 리프레시 토큰입니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),

    // 이미지 분석 (500, 400)
    IMAGE_ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "이미지 분석에 실패했습니다"),
    IMAGE_UPLOAD_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "업로드된 이미지를 찾을 수 없습니다"),

    // 정리 (409)
    ORGANIZE_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 정리 작업이 있습니다")
    ;

    private final HttpStatus httpStatus;
    private final String message;

    public String getCode() {
        return this.name();
    }
}