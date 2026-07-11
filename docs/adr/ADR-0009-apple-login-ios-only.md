# ADR-0009: Apple 로그인을 iOS 전용으로 제한

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-10 |
| 관련 | [LLD-0001](../lld/LLD-0001-oauth-social-login.md), [ADR-0008](ADR-0008-exchange-code-deeplink-pattern.md)(Superseded) |

## 맥락 (Context)

Apple은 Android용 네이티브 Sign in with Apple SDK를 제공하지 않는다.
Android에서 Apple 로그인을 지원하려면 웹 기반 OAuth 플로우가 필요하고,
이 경우 다음이 추가로 필요했다.

- Apple Services ID 생성 및 Return URL 등록
- 서버가 Apple로부터 콜백을 직접 받는 별도 엔드포인트
- 커스텀 URI 스킴 가로채기 위험에 대응하는 exchangeCode 패턴(ADR-0008)
- 딥링크 스킴을 Android 클라이언트와 별도로 합의·등록

이미 이 구조를 설계하고 일부 콘솔 설정까지 진행했으나, 실제로 이 복잡도를
감수하면서까지 Android에서 Apple 로그인을 지원할 필요가 있는지 재검토했다.

## 결정 (Decision)

**Apple 로그인은 iOS에서만 제공한다. Android는 카카오 로그인만 제공한다.**
Android + Apple을 위해 설계했던 서버 콜백 흐름, exchangeCode 패턴, 이중
audience 검증을 전부 제거하고, 모든 로그인을 "앱이 SDK로 토큰을 직접 받아
서버로 전달"하는 단일 패턴으로 통일한다.

## 고려한 대안 (Considered Options)

1. **Android + Apple 웹 플로우 유지 (기각)** — 이미 설계·구현 착수한
   상태였으나, 이 조합 하나를 위해 서버 콜백 엔드포인트, 딥링크 보안
   설계(ADR-0008), Services ID 인프라를 전부 유지보수해야 하는 비용이
   지속적으로 발생한다.
2. **Apple 로그인 iOS 전용으로 제한 (채택)** — App Store 심사 요건(카카오
   로그인 제공 시 Apple 로그인 필수)은 **iOS 앱**에 적용되는 규정이다.
   Android 앱에는 이 규정 자체가 적용되지 않으므로, Android에서 Apple
   로그인을 지원하지 않아도 App Store 심사 요건 위반이 아니다.

## 결과 (Consequences)

### 긍정
- 인증 로직이 단일 패턴으로 통일되어 구현·테스트·유지보수 비용이
  크게 줄어든다.
- Android 클라이언트가 딥링크 스킴 등록, state 검증, exchangeCode 교환
  같은 추가 구현을 하지 않아도 된다.
- 서버에 Apple 콜백 전용 엔드포인트와 인메모리 캐시 인프라가 필요 없어진다.

### 부정 / 트레이드오프
- Android 사용자 중 카카오 계정이 없고 Apple 계정만 있는 사용자는
  RECAP에 가입할 수 없다. 이 사용자군의 규모는 확인되지 않았다(불확실 —
  실제 영향은 추후 지표로 관찰 필요).
- 이미 생성한 Apple Services ID, Return URL 등록은 더 이상 사용되지
  않는다. 즉시 삭제할 필요는 없으나 관리 대상에서는 제외된다.

## 후속 / 미결정

- [ ] Android에서 카카오 계정이 없는 사용자의 가입 이탈 규모를 추후
      지표로 관찰할지 여부 (제품 논의 필요)
- [ ] Apple Services ID(`com.cmc.recap.service`) 삭제 여부 — 급하지 않음
