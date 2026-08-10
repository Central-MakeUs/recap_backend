# LLD-0020: MDC 기반 userId 로그 태깅

> Low-Level Design. 기능 구현 전 설계를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-08-11 |
| 관련 | LLD-0016(AI 동의 관리) |

## 개정 이력

- 2026-08-11: 실제 `JwtAuthenticationFilter` 구조(`extractToken()
  .ifPresent(this::authenticate)` 패턴)에 맞춰 예시 코드 수정.
  Spring Boot 4.1.0의 기본 콘솔 로그 패턴을 실측으로 확인해
  문서에 반영(타임스탬프가 ISO-8601로 변경된 것 포함).

## 맥락 (Context)

운영 중 "동의 이력을 넣었는데도 403이 계속 난다"는 문의를 조사하며,
실제로는 코드가 완전히 정상이었고 조사 대상 계정을 잘못 짚었던
게(21번 vs 실제 14번) 원인이었다. 로그에 "이 요청을 누가 보냈는지"가
전혀 안 남아있어, `refresh_tokens` 테이블 시각을 역추적하는 데만
20분 넘게 걸렸다. 앞으로 이런 조사를 로그 한 줄로 끝낼 수 있도록
개선한다.

## 결정 (Decision)

### 로그 레벨은 그대로 둔다

DEBUG로 전체 로그 레벨을 올리는 방식은 채택하지 않는다 — 이미
Hibernate SQL 디버그 로그로 넘쳐나는 상황을 악화시킬 뿐, "누가"라는
정보를 주지 못한다. 대신 **기존 로그 줄에 식별 정보를 자동으로
덧붙이는** 방식을 택한다.

### MDC(Mapped Diagnostic Context) 사용

SLF4J/Logback이 기본 제공하는 스레드 로컬 기반 컨텍스트. 한 번
설정해두면, 그 이후 그 스레드에서 찍히는 모든 로그 줄(직접 작성한
것, Spring/Hibernate가 내부적으로 찍는 것 모두)에 자동으로 값이
따라붙는다.

```java
// JwtAuthenticationFilter.java
@Override
protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
) throws ServletException, IOException {
    extractToken(request).ifPresent(this::authenticate);
    try {
        filterChain.doFilter(request, response);
    } finally {
        MDC.clear();
    }
}

private void authenticate(String token) {
    try {
        Long userId = jwtProvider.getUserId(token);
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MDC.put("userId", String.valueOf(userId));
    } catch (JwtException | IllegalArgumentException e) {
        // 유효하지 않거나 만료된 토큰 — 인증을 설정하지 않고 넘어간다.
    }
}
```

**`finally`에서 반드시 `MDC.clear()`를 호출한다.** Tomcat은 스레드를
재사용하므로, 요청 A(userId=14) 처리 후 MDC를 안 지우면 다음에 같은
스레드가 처리하는 비로그인 요청 B에도 실수로 "userId=14"가 찍혀
완전히 잘못된 정보로 오인할 위험이 있다.

### 로그 패턴에 추가

`logback-spring.xml` 등 커스텀 logback 설정이 없어 Spring Boot
기본 콘솔 패턴을 그대로 쓰고 있었다. 이 기본 패턴에 `%m` 바로
앞에 `[userId=%X{userId:-}]`만 삽입한 전체 문자열을
`application-local.yml`, `application-prod.yml` 양쪽의
`logging.pattern.console`에 각각 추가했다(공통 `application.yml`은
없어 파일당 중복 기재).

```yaml
logging:
  pattern:
    console: "${CONSOLE_LOG_PATTERN:-%clr(%d{${LOG_DATEFORMAT_PATTERN:-yyyy-MM-dd'T'HH:mm:ss.SSSXXX}}){faint} %clr(${LOG_LEVEL_PATTERN:-%5p}) %clr(${PID:- }){magenta} %clr(---){faint} %clr([%15.15t]){faint} %clr(%-40.40logger{39}){cyan} %clr(:){faint} [userId=%X{userId:-}] %m%n${LOG_EXCEPTION_CONVERSION_WORD:-%wEx}}"
```

`%X{userId:-}`는 MDC에 `userId`가 없으면 빈 문자열로 처리한다(콜론
뒤 기본값 문법).

**Boot 4.1.0에서 날짜 포맷 기본값이 ISO-8601로 바뀐 것을 실측으로
확인함.** 문서 초안 작성 시 참고한 `LOG_DATEFORMAT_PATTERN` 기본값
(`yyyy-MM-dd HH:mm:ss.SSS`)은 구버전 기준이었고, 실제 로컬에서
`bootRun`으로 찍힌 로그(`2026-08-10T22:37:21.946Z ...`)를 대조한
결과 `yyyy-MM-dd'T'HH:mm:ss.SSSXXX` 형식(ISO-8601, UTC)이 적용되고
있었다. 나머지 항목(레벨/PID/스레드명/logger명 폭)은 실측 결과와
일치해 그대로 유지했다.

### 적용 전/후 비교

```
적용 전:
  WARN ... GlobalExceptionHandler : Business Exception:
  code=AI_CONSENT_REQUIRED, message=AI_CONSENT_REQUIRED

적용 후:
  WARN ... GlobalExceptionHandler : [userId=14] Business Exception:
  code=AI_CONSENT_REQUIRED, message=AI_CONSENT_REQUIRED
```

### 테스트

`MDC.get("userId")`가 필터 체인 실행 중에는 올바르게 설정되고,
실행 후에는 `null`(정리됨)인지 확인하는 단위 테스트를 추가한다 —
가짜 `FilterChain`(Mockito mock)의 `doFilter()` 호출 시점에 MDC 값을
캡처하는 방식으로 검증 가능.

## 고려한 대안 (Considered Options)

1. **전체 로그 레벨을 DEBUG로 상향 (기각)** — 이미 SQL 디버그
   로그로 넘쳐나는 상황을 악화시킴, "누가"라는 정보 자체는 여전히
   안 줌.
2. **`GlobalExceptionHandler` 등 개별 로그 코드에 매번 userId
   파라미터 수동 추가 (기각)** — 코드 여러 곳을 일일이 고쳐야 하고,
   앞으로 추가되는 로그 코드마다 계속 신경 써야 함. MDC는 필터
   한 곳만 고치면 이후 모든 로그에 자동 적용됨.

## 결과 (Consequences)

### 긍정
- 앞으로 "이 요청을 누가 보냈는지" 확인이 `grep` 한 번으로 끝남.
- 로그 코드를 개별적으로 수정할 필요 없이, 필터 한 곳의 변경만으로
  전체 애플리케이션 로그에 적용됨.

### 부정 / 트레이드오프
- MDC 값 정리(`clear()`)를 빠뜨리면 스레드 재사용 시 잘못된 정보가
  섞일 위험이 있음 — `finally` 블록으로 방어하지만, 향후 필터 체인
  구조가 바뀌면 이 부분을 다시 확인해야 함.

## 후속 / 미결정

- [ ] `userId` 외에 `requestId`(요청 하나마다 고유값)도 같이
      MDC에 넣을지 — 여러 스레드의 로그가 뒤섞일 때 "같은 요청에서
      나온 로그인지" 구분하는 데 유용할 수 있음, 이번 범위는 아님
