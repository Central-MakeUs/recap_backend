# ADR-0013: 이미지 분석 파이프라인 구조 — 1단계 통합형 + ImageAnalysisProvider 인터페이스

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-15 |
| 관련 | [ADR-0010](ADR-0010-original-image-s3-storage.md)(원본 이미지 S3 저장), [ADR-0011](ADR-0011-infocard-flat-summary-fields.md)(평문 컬럼), [LLD-0002](../lld/LLD-0002-capture-upload-organize-explore.md) |

## 맥락 (Context)

RECAP은 03-03(정리 시작)~07-01(정보카드 상세)에서 스크린샷 이미지를
분석해 `CardType`/`title`/`summary`/`body`/`extractedText`를 산출해야
한다. 이 분석을 어떻게 구성할지 결정이 필요했다.

## 결정 (Decision)

파이프라인은 **"1단계 통합형"**으로 간다: 이미지를 비전 지원 LLM에
한 번에 넘겨서 분류·요약·텍스트 추출을 동시에 받는다. 별도 OCR
단계를 분리하거나(2단계형) 서버에 OCR을 직접 설치하는(자체 호스팅)
방식은 채택하지 않는다.

`ImageAnalysisProvider` 인터페이스로 벤더를 추상화한다
(`KakaoOAuthProvider`/`AppleOAuthProvider`와 동일한 패턴):

```java
public interface ImageAnalysisProvider {
    ImageAnalysisResult analyze(String imageKey);
}

public record ImageAnalysisResult(
        CardType type, String title, String summary, String body, String extractedText
) {}
```

구체적 벤더/모델은 이 ADR에서 확정하지 않는다(후속 미결정 참고).

## 고려한 대안 (Considered Options)

1. **2단계 분리형(OCR API → 텍스트 LLM 분류) (기각)** — 호출 두 번으로
   지연시간·실패지점이 늘고, RECAP 스크린샷은 스캔 문서가 아니라
   깨끗한 UI 캡처라 비전 LLM이 한 번에 처리해도 정확도 손실이 크지
   않을 것으로 판단.
2. **자체 호스팅 OCR(Tesseract/PaddleOCR) + 텍스트 LLM (기각)** —
   t3.small 단일 인스턴스에 OCR 엔진을 얹는 리소스 부담이 크고, 한글
   인식률이 상용 비전 LLM보다 떨어질 가능성이 높음.
3. **1단계 통합형 (채택)** — 구현 단순, 실패 지점 하나, RECAP 이미지
   특성(깨끗한 UI 캡처)에 적합.

## 결과 (Consequences)

### 긍정
- 구현 단순, `OAuthProvider`와 동일 패턴이라 팀에 익숙함.
- 벤더 교체 비용이 구현체 하나로 국한됨.

### 부정 / 트레이드오프
- 비전 토큰 비용이 텍스트 전용 호출보다 비쌈.
- 벤더 하나에 OCR·분류·요약을 전부 맡기므로 벤더 품질에 전적으로
  의존.

## 후속 / 미결정

- [x] 구체적 벤더/모델 확정 — Google Gemini(gemini-2.5-flash)로
  확정됨, [LLD-0003](../lld/LLD-0003-ai-image-analysis-module.md) 참고
- [x] 프롬프트 설계, 응답 JSON 스키마 강제 방식 — LLD-0003에서 확정
  (구조화 출력 `response_schema`), 이후 [LLD-0008](../lld/LLD-0008-body-type-templates.md)에서
  body 유형별 템플릿으로 개정
- [x] 분석 실패 시 재시도 정책 — LLD-0003에서 확정(5xx/429/타임아웃
  1회 재시도, 4xx 즉시 실패)
- [ ] 스크린샷에 담긴 개인정보(주소, 예약정보 등)가 외부 AI 벤더로
  전송된다는 점 — 개인정보처리방침 고지 필요 여부 PM 확인 필요