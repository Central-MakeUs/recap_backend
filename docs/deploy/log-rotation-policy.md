## Docker 로그 순환 정책

### 배경
기존에는 `/etc/logrotate.d/docker-container`에 `daily / rotate 7 /
copytruncate` 방식으로 컨테이너 로그(`/var/lib/docker/containers/*/*.log`)를
순환시켰다.

이 방식에서 `docker logs --tail`이 멈추는 문제를 실제로 겪었다.
원인은 `copytruncate`가 로그 파일을 잘라내는 방식과, Docker
데몬이 해당 로그 파일 내부에서 읽기 위치(offset)를 추적하는
방식이 충돌하기 때문이다. logrotate가 파일을 truncate하면 Docker
쪽 오프셋 정보가 어긋나 `docker logs`가 이후 로그를 정상적으로
읽지 못하고 멈추는 현상이 발생했다.

이를 근본적으로 피하기 위해, logrotate + copytruncate 대신
Docker 자체 내장 로그 순환(`json-file` 드라이버의 `max-size`/
`max-file` 옵션)으로 교체했다. Docker 내장 순환은 파일을
truncate하지 않고 새 파일로 교체하는 방식이라 오프셋 충돌이
발생하지 않는다.

### 현재 설정
`docker-compose.yml`의 `app`, `nginx` 서비스 각각에 아래 설정을
적용했다.

```yaml
logging:
  driver: "json-file"
  options:
    max-size: "20m"
    max-file: "7"
```

- 컨테이너당 로그 파일 최대 20MB × 최대 7개 = 최대 약 140MB 보관
- 보관 기간은 "N일"로 고정되지 않는다. 로그 발생량에 따라 가변적임
- 참고용 실측 기준: 과거 30일치 로그가 약 22만 줄이었던 사례
  기준으로 환산하면 대략 7일 안팎에 해당하는 용량이지만, 이는
  트래픽/로그 레벨 변화에 따라 달라질 수 있는 추정치이며 SLA성
  보장이 아니다.

### 제거된 설정
- `/etc/logrotate.d/docker-container` (logrotate + copytruncate
  방식) — Docker 내장 순환과 동시에 돌 경우 동일한 충돌이 재발할
  수 있어 제거했다.

### 검증
설정 변경 후 컨테이너 재생성(`docker compose up -d`) 시점에
`docker logs recap-app --tail 100`, `docker logs recap-nginx --tail 100`이
지연 없이 즉시 반환되는지 확인했다.
