# LLD (Low-Level Design)

새 기능 구현 전 상세 설계를 기록한다.
새 LLD는 `../templates/lld.md`를 복사해 `LLD-XXXX-<slug>.md`로 만든다.

작성 기준:
- 새 기능은 LLD 작성 후 구현한다. LLD가 없으면 작성을 먼저 제안한다.
- "미결정 사항"은 추측으로 채우지 않는다. 반드시 질문한다.
- 설계가 뒤집히면 문서를 수정하지 않고 새 LLD를 만들고 기존 문서를
  `Superseded by LLD-YYYY`로 표기한다.

| 번호 | 제목 | 상태 | 날짜 |
| --- | --- | --- | --- |
| [LLD-0001](LLD-0001-oauth-social-login.md) | 카카오/Apple 소셜 로그인 | Accepted (개정) | 2026-07-10 (최초 2026-07-07) |
| [LLD-0002](LLD-0002-capture-upload-organize-explore.md) | 스크린샷 업로드 · 정리 · 탐색 플로우 | Accepted | 2026-07-13 |
| [LLD-0003](LLD-0003-ai-image-analysis-module.md) | AI 이미지 분석 모듈 (Gemini 구현체) | Accepted | 2026-07-15 (소급 기록) |
| [LLD-0004](LLD-0004-home-summary-api.md) | 홈 화면 API (GET /home/summary) | Accepted | 2026-07-16 |
| [LLD-0005](LLD-0005-capture-detail-favorite-delete.md) | 정보카드 상세조회 · 즐겨찾기 · 삭제 API | Accepted | 2026-07-17 |
| [LLD-0006](LLD-0006-storage-api.md) | 보관함 API (즐겨찾기 · 기타 · 유형별 보기) | Accepted | 2026-07-17 |
| [LLD-0007](LLD-0007-search-api.md) | 검색 API | Accepted | 2026-07-18 |
