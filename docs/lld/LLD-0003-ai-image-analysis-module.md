# LLD-0003: AI 이미지 분석 모듈 (Gemini 구현체)

> Low-Level Design. 기능 구현 전 설계를 기록한다.
>
> **주의**: 이 문서는 이례적으로 구현이 상당 부분 진행된 뒤 소급 작성됐다
> (ADR-0013 → 이슈 → STAGE 프롬프트로 바로 진행되어 `implement` 스킬의
> "LLD 없이 구현 시작 금지" 규칙을 건너뛴 상태였음). Claude Code는 이
> 문서 내용을 실제 코드와 대조해 어긋나는 부분이 있으면 바로잡아야 한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-15 (소급 기록) |
| 관련 | [ADR-0013](../adr/ADR-0013-image-analysis-pipeline.md), [LLD-0002](LLD-0002-capture-upload-organize-explore.md), 이슈 #13 |

## 맥락 (Context)

ADR-0013에서 "1단계 통합형 + `ImageAnalysisProvider` 인터페이스" 구조를
확정했으나, 구체적 벤더·모델·프롬프트·에러 처리 방식은 후속 미결정으로
남겨뒀다. 이 문서는 그 미결정 사항들이 실제로 어떻게 확정됐는지 기록한다.

## 결정 (Decision)

### 벤더/모델

```
벤더: Google Gemini
모델: gemini-2.5-flash (환경변수 GEMINI_MODEL_NAME, 기본값으로 지정)
```

무료/저가 티어에서 시작하되, 이미지+한글 OCR+9종 분류+요약이라는 복합
작업 특성상 Flash-Lite보다 한 단계 위인 Flash로 시작. 결제는 활성화된
상태(무료 티어의 "데이터가 학습에 쓰일 수 있음" 리스크 회피 — RECAP
스크린샷에 개인정보가 담길 수 있다는 ADR-0013의 우려와 직결).

### 이미지 전달 방식

서버가 S3에서 원본 바이트를 직접 읽어(`S3Client.getObjectAsBytes`)
Base64로 인코딩해 Gemini 요청에 inline으로 포함한다. presigned URL을
Gemini에 그대로 넘기는 방식은 채택하지 않았다 — 만료 시점과 외부
네트워크 도달성 문제를 피하기 위함.

**mimeType은 하드코딩하지 않는다.** S3 오브젝트의 실제 `Content-Type`
메타데이터(`getObjectAsBytes(...).response().contentType()`)를 그대로
사용한다. presigned PUT 업로드 시 클라이언트가 보낸 `Content-Type`
헤더가 S3에 그대로 남기 때문. 이 값이 null/blank면 `IMAGE_ANALYSIS_FAILED`로
즉시 실패 처리한다(추측으로 기본값을 채우지 않음).

### Gemini 요청 형식

```
POST https://generativelanguage.googleapis.com/v1beta/models/{modelName}:generateContent
Header: x-goog-api-key: {GEMINI_API_KEY}
```

**요청 바디 필드명은 전부 snake_case로 확정됐다** (공식 문서로 재검증
완료): `generation_config`, `response_mime_type`, `response_schema`,
`inline_data`, `mime_type`. Java 요청 DTO는 `@JsonProperty`로 이
이름들을 명시적으로 매핑해야 한다 — camelCase 그대로 두면 API가 조용히
무시하고 구조화 출력 강제가 깨진다.

`response_schema`의 `type` 값은 **대문자**(`OBJECT`, `STRING` 등)로
지정해야 한다.

### 프롬프트 (확정본)

```
당신은 사용자가 저장한 스크린샷 이미지를 분석해서, 나중에 쉽게 다시
찾아볼 수 있는 정보 카드로 정리하는 어시스턴트입니다.

이미지를 분석해서 아래 형식의 JSON으로만 응답하세요. 다른 설명이나
문장은 절대 추가하지 마세요.

{
  "type": "JOB | SHOPPING | PLACE | SCHEDULE | KNOWLEDGE | CONTENT | BENEFIT | RECORD | ETC 중 하나",
  "title": "핵심 내용을 요약한 제목",
  "summary": "한 줄 요약",
  "body": "AI가 정리한 상세 설명",
  "extractedText": "이미지에 보이는 모든 텍스트 원문"
}

## 유형(type) 분류 기준 — 반드시 아래 9개 중 정확히 1개만 선택
- JOB: 채용 공고, 이력서, 지원 관련 정보
- SHOPPING: 상품 정보, 가격 비교, 주문/배송 내역
- PLACE: 맛집, 장소, 지도, 위치 정보
- SCHEDULE: 예약 확인, 일정, 티켓, 캘린더
- KNOWLEDGE: 유용한 정보, 지식, 설명 글, 뉴스
- CONTENT: 책, 영화, 음악, 콘텐츠 추천/소개
- BENEFIT: 할인, 쿠폰, 이벤트, 혜택
- RECORD: 메모, 개인 기록, 채팅, 단순 캡처
- ETC: 위 8개 중 어디에도 명확히 속하지 않는 경우

애매하면 가장 가까운 유형을 고르고, 정말 판단이 어려운 경우에만
ETC를 선택하세요.

## 각 필드 작성 규칙
- title: 이미지의 핵심 내용을 나타내는 제목. 1자 이상 30자 이내.
  줄바꿈 없이 한 줄로 작성하세요.
- summary: title보다 조금 더 구체적인 한 줄 요약. 80자 이내.
  줄바꿈 없이 작성하세요.
- body: 이미지 내용을 사람이 읽기 편하게 정리한 상세 설명. 이미지에
  보이는 텍스트를 그대로 옮겨 적지 말고, 핵심 정보를 자연스러운
  문장으로 재구성하세요. {BODY_MAX_LENGTH}자 이내로 작성하세요.
- extractedText: 이미지에 보이는 텍스트를 최대한 빠짐없이 그대로
  옮겨 적으세요. 이 필드는 검색용으로만 쓰이며 사용자에게 보이지
  않습니다. body처럼 재구성하지 말고, 원문 그대로 나열하세요.

## 주의사항
- 반드시 위 JSON 형식으로만 응답하세요. 코드블록이나 다른 설명 문장을
  포함하지 마세요.
- 이미지에서 전화번호, 예약번호, 주소 등 정보가 보이면 판단해서
  생략하지 말고 있는 그대로 추출하세요 (이런 정보를 찾기 쉽게
  정리하는 게 이 서비스의 핵심 목적입니다).
- 이미지 내용이 불분명하거나 텍스트가 거의 없는 경우에도, 보이는
  정보만으로 최선을 다해 채우세요.
```

`{BODY_MAX_LENGTH}`는 Java 상수(현재 500, 임시값 — 아래 후속 참고)로
치환되어 실제 요청에 포함된다.

### 응답 파싱

`candidates[0].content.parts[0].text`에 담긴 JSON 문자열을
`ObjectMapper`로 파싱한다. **JSON 파싱 실패와 `CardType.valueOf()`
매핑 실패를 하나의 try 블록으로 묶어 전부 `BusinessException(IMAGE_ANALYSIS_FAILED)`로
통일 처리한다** — 원인은 `cause`로 남긴다.

### 에러 코드 매핑

| 상황 | ErrorCode | HTTP |
| --- | --- | --- |
| S3에서 `NoSuchKeyException`(objectKey 없음) | `IMAGE_UPLOAD_VERIFICATION_FAILED` | 400 |
| S3 네트워크/자격증명 오류(`SdkException`, `S3Exception` 포함) | `IMAGE_ANALYSIS_FAILED` | 500 |
| Content-Type null/blank | `IMAGE_ANALYSIS_FAILED` | 500 |
| Gemini 응답 JSON 파싱 실패, CardType 매핑 실패 | `IMAGE_ANALYSIS_FAILED` | 500 |
| Gemini 5xx/429/타임아웃 (재시도 후에도 실패) | `IMAGE_ANALYSIS_FAILED` | 500 |
| Gemini 4xx (재시도 없이 즉시) | `IMAGE_ANALYSIS_FAILED` | 500 |

**`IMAGE_UPLOAD_VERIFICATION_FAILED`가 400인 이유**: RECAP은 404를
"URL 경로로 직접 지정한 리소스를 못 찾을 때"에만 써왔다(`GET /users/{id}`
등). 이번 건은 요청 본문(`imageKeys`) 안의 참조값이 잘못된 경우라
`INVALID_INPUT`과 같은 층위(400)로 맞춘다.

### 재시도 정책

```
재시도 대상 (최대 1회, 고정 지연 후): 5xx, 429(TOO_MANY_REQUESTS),
                                       타임아웃(ResourceAccessException)
재시도 비대상 (즉시 실패): 그 외 4xx
```

사용자에게 노출되는 "재시도 버튼"은 제공하지 않는다(03-D 정리 실패
화면 정책, 제품 레벨 결정과 별개). 이건 순수 인프라 레벨의 일시적
오류 방어다.

### title/summary 길이 초과 처리 (truncate)

```
InfoCard.TITLE_MAX_LENGTH = 30, SUMMARY_MAX_LENGTH = 80 (public static final)
```

Gemini의 `response_schema`는 JSON *구조*만 강제하고 문자열 *길이*는
강제하지 못한다. LLM이 프롬프트의 글자 수 지시를 항상 지킨다는 보장이
없어, 코드 레벨 방어가 필요하다.

**truncate 로직은 `ImageAnalysisResult`나 `GeminiImageAnalysisProvider`
안에 두지 않는다.** 이는 벤더 중립적이어야 할 타입에 InfoCard 도메인
지식이 침범하는 잘못된 결합이다. 대신 `InfoCard`가 길이 상수를 공개하고,
정리 서비스가 `InfoCard.create()` 호출 직전에 그 상수를 참조해 자른다.

**자르기는 반드시 코드 포인트 단위로 한다** (`substring(0, N)` 금지).
UTF-16 code unit 기준으로 자르면 서로게이트 쌍(이모지 등)을 깨뜨려
DB에 손상된 문자열이 들어갈 수 있다. 초과 시 말줄임표(`…`)를 붙여
전체 길이를 상수값에 맞춘다.

## 고려한 대안 (Considered Options)

1. **mimeType을 `.jpg` 확장자 기준으로 고정 (기각)** — 오브젝트 키의
   확장자와 실제 업로드된 바이트의 포맷이 일치한다는 보장이 없음
   (iOS/Android 스크린샷은 PNG인 경우가 흔함).
2. **truncate를 `ImageAnalysisResult`에 위임 (기각)** — 벤더 교체
   시(ADR-0013의 의도) 모든 벤더 구현체에 InfoCard 길이 규칙을
   중복시켜야 함.
3. **truncate를 `InfoCard` 생성자 안에서 조용히 수행 (기각)** — "생성
   시점 검증 후 위반 시 예외"라는 Always-Valid 계약과 다른 책임(정규화)이라
   엔티티에 넣지 않는다(domain-design-principles.md #1).
4. **길이 초과 시 그 이미지를 실패 처리 (기각)** — 분석 자체는 성공했는데
   글자 수 초과만으로 이미지 전체를 버리는 건 사용자 손실이 과함. 재시도
   버튼도 없어(제품 정책) 그 이미지는 영구 유실됨.

## 결과 (Consequences)

### 긍정
- 요청/응답 필드명을 공식 문서로 재검증해 "컴파일은 되는데 조용히
  틀리는" 위험을 사전에 차단.
- mimeType/에러코드 분리로 실패 원인(업로드 누락 vs 분석 실패)이
  로그에서 구분됨.
- truncate 위치 설계로 향후 벤더 교체 시 중복 없이 재사용 가능.

### 부정 / 트레이드오프
- 프롬프트가 90% 정확도로 글자 수를 지켜도, 나머지는 매번 truncate가
  개입해 정보가 잘릴 수 있음 — 프롬프트 튜닝으로 이 비율을 낮추는
  게 근본 해결책이나 지금은 코드 방어로만 대응.
- Gemini 응답 포맷이 벤더 업데이트로 바뀌면(필드명, 스키마 문법 등)
  이 문서와 코드가 함께 갱신되어야 함.

## 후속 / 미결정

- [ ] `BODY_MAX_LENGTH = 500`은 임시값 — API 명세서 검수 때부터
  미확정으로 남아있던 항목, 확정 필요
- [ ] Gemini 무료 → 유료 티어 전환 시점 (현재 유료 활성화는 되어
  있으나 실제 트래픽 기준 재검토 필요)
- [ ] 프롬프트가 실제 운영에서 얼마나 정확히 글자 수 제약을 지키는지
  관찰 후, truncate 발생 빈도가 높으면 프롬프트 재설계 검토
- [ ] Gemini 응답이 지연되거나 실패하는 실제 빈도 관찰 → 재시도
  횟수(현재 1회)가 충분한지 재검토