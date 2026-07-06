# ADR-0005: JPA 생명주기 관리 컴포넌트의 스프링 DI 배제 원칙

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-07 (소급 기록 — 실제 장애/해결은 2026-07-04) |
| 관련 | CardSummaryConverter, ADR-0002, 트러블슈팅 문서(JPA Converter) |

## 맥락 (Context)

`CardSummaryConverter`(JPA `AttributeConverter`)가 `ObjectMapper`를 생성자
주입으로 받도록 작성되자, 애플리케이션 기동이 다음 에러로 실패했다.

```
Parameter 0 of constructor in ...CardSummaryConverter required a bean of
type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

원인 메커니즘 (스택: Spring Boot 4.1.0 / Hibernate 7.4.1):

1. `@Converter` 클래스는 스프링 빈이 아니지만, Hibernate `BeanContainer`
   SPI와 스프링의 `SpringBeanContainer` 연동으로 생성이 스프링에 위임된다.
2. 등록된 빈이 아니므로 스프링은 생성자 기반 autowire를 대체 실행한다.
3. 이 경로에서 `ObjectMapper` 빈 해석이 실패하며 컨텍스트 초기화가 중단됐다.
4. 근본적으로 JPA 스펙(JSR-338)은 컨버터에 **public 무인자 생성자**를
   요구한다. 생성자 의존성은 스펙 밖의 "묵시적 편의 기능"에 기대는 구조다.

정확히 어떤 버전 조합 조건에서 이 연동이 실패하는지는 검증하지 못했다
(불확실). 따라서 버전 의존적 동작에 기대지 않는 방향으로 결정한다.

## 결정 (Decision)

- `AttributeConverter`, `EntityListener` 등 **JPA/Hibernate가 생명주기를
  관리하는 컴포넌트에는 스프링 생성자 DI를 걸지 않는다.**
- 필요한 협력 객체는 컴포넌트 전용으로 직접 생성해 소유한다.
  `CardSummaryConverter`는 `JavaTimeModule`을 명시 등록한 **static
  ObjectMapper**를 보유한다 (LocalDate/LocalTime 직렬화 보장).
- "프레임워크의 자동 편의 기능"과 "스펙의 표준 계약"이 충돌하면 **표준 계약을
  우선**한다.

## 고려한 대안 (Considered Options)

1. **전용 static ObjectMapper (채택)** — 스프링/하이버네이트 버전 변화에
   완전 독립. DB 저장용 Jackson 설정을 API 응답 설정과 분리 관리 (이 컨버터의
   책임은 DB 직렬화뿐이므로 SRP 관점에서도 자연스러움).
2. **전역 ObjectMapper 정적 홀더 (@Component + static 참조)** — 앱 전역
   Jackson 설정과 완전 통일이 필요할 때 유효. 현재는 그 요구가 없어 미채택.
3. **빈 컨테이너 연동 설정 튜닝** — 버전이 바뀔 때마다 다시 깨질 수 있는
   기반 위에 쌓는 방식이라 기각.

## 결과 (Consequences)

### 긍정
- 기동 실패 해소, 버전 업그레이드 시 재발 리스크 제거.
- 컨버터가 스프링 컨텍스트 없이도 단위 테스트 가능.

### 부정 / 트레이드오프
- DB 저장 JSON과 API 응답 JSON의 Jackson 설정이 이원화됨 — 날짜 포맷 등을
  통일해야 할 요구가 생기면 대안 2로 전환 검토.
- 새 날짜/시간 타입 필드 추가 시 모듈 등록 누락에 주의 필요.

## 후속 / 미결정
- [ ] EntityListener 도입 시 본 원칙 적용 여부 재확인
- [ ] 상세 인과와 재발 방지 체크리스트는 트러블슈팅 문서 참조
