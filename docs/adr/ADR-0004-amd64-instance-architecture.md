# ADR-0004: 빌드-런타임 아키텍처 일치를 위한 amd64(x86_64) EC2 채택

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-07 (소급 기록 — 실제 전환은 2026-06-26) |
| 관련 | Dockerfile, deploy.yml (GitHub Actions) |

## 맥락 (Context)

최초 인프라는 비용 효율을 이유로 t4g(ARM64, AWS Graviton) 인스턴스를
선택했다. 그러나 CI 빌드 환경과 런타임 아키텍처가 어긋나며 실제 장애를 겪었다.

- GitHub Actions 기본 runner는 x86_64. `platforms: linux/arm64`로 빌드 시
  QEMU 에뮬레이션을 경유한다.
- 에뮬레이션 환경에서 Dockerfile 내 Gradle 빌드가 `exec /bin/sh: exec format
  error`(exit 255)로 반복 실패했다.
- `eclipse-temurin:*-alpine` 계열 이미지가 ARM64 매니페스트를 제공하지 않아
  베이스 이미지 선택도 제약되었다 (jammy로 우회 필요).
- 워크어라운드로 "Actions(x86_64)에서 jar 빌드 → Dockerfile은 복사만" 구조가
  동작했으나, Dockerfile 내 멀티 스테이지 Gradle 빌드(레퍼런스 프로젝트들의
  표준 패턴)를 쓸 수 없는 제약이 남았다.

## 결정 (Decision)

EC2를 **t3.small(amd64, Ubuntu 24.04)** 로 교체해 CI runner와 런타임
아키텍처를 일치시킨다. 이로써 Dockerfile 내 멀티 스테이지 Gradle 빌드와
alpine 계열 베이스 이미지 사용이 모두 가능해진다. 기존 VPC/서브넷/보안그룹/
Elastic IP/RDS 연결은 그대로 유지하고 인스턴스만 재생성했다.

## 고려한 대안 (Considered Options)

1. **ARM64 유지 + jar 빌드 분리** — 동작은 확인됨. 그러나 Dockerfile 안에서
   빌드 재현이 불가능하고(로컬-CI 빌드 경로 이원화), alpine 미지원 등 제약 지속.
2. **ARM64 유지 + ARM 셀프호스티드 러너** — 아키텍처 일치는 되나 러너 운영
   비용과 관리 부담이 MVP 규모에 과함.
3. **amd64 전환 (채택)** — 빌드 파이프라인 단순화가 t4g 대비 비용 증가보다
   가치가 크다고 판단.

## 결과 (Consequences)

### 긍정
- CI-런타임 아키텍처 일치로 exec format error 계열 문제 원천 소멸.
- Dockerfile 내 표준 멀티 스테이지 빌드 사용 가능 (빌드 경로 단일화).
- 베이스 이미지 선택 자유 (eclipse-temurin alpine 계열 포함).

### 부정 / 트레이드오프
- Graviton(t4g) 대비 동급 성능당 비용 상승 (AWS가 Graviton의 가격 효율
  우위를 홍보하는 만큼, 규모가 커지면 재평가 여지 있음).
- 추후 ARM 회귀를 원하면 본 문제(에뮬레이션 빌드)를 다시 풀어야 함.

## 후속 / 미결정
- [ ] 트래픽 증가로 인스턴스 스케일업 검토 시, ARM 셀프호스티드 러너 또는
      네이티브 ARM 빌드 지원 상황을 재평가
