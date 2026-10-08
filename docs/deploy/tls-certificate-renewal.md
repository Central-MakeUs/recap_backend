## TLS 인증서 발급/갱신 구조

### 1. 개요

- 도메인: `re-cap.duckdns.org`
- 발급기관: Let's Encrypt (`https://acme-v02.api.letsencrypt.org/directory`)
- 인증서 유효기간: 90일
- 자동 갱신 시도 시작 시점: 만료 30일 전부터 (`renew_before_expiry = 30 days`)
- 현재 인증서 만료일: **2027-01-06 08:47:29 UTC** (`sudo certbot certificates` 기준, 2026-10-08 조회)

### 2. 현재 구조 (webroot 방식, 2026-10-08부터)

ACME HTTP-01 챌린지를 nginx가 서빙하는 정적 경로(webroot)로 통과시키는 방식이다.
이전의 `standalone` 방식(80번 포트를 certbot이 직접 점유)은 nginx와 포트가 충돌해
갱신이 계속 실패했던 원인이므로 폐기했다 (3장 참고).

```
[certbot.timer] --하루 2회(+랜덤 지연)--> [certbot renew]
                                              │
                                   HTTP-01 챌린지 파일 생성
                                              │
                                   /home/ubuntu/recap/certbot-webroot/.well-known/acme-challenge/*
                                              │
                                   (nginx가 80번 포트에서 정적 서빙, bind mount)
                                              │
                                   Let's Encrypt가 http://re-cap.duckdns.org/... 로 검증
                                              │
                                   갱신 성공 시 deploy_hook 실행
                                              │
                                   docker exec recap-nginx nginx -s reload (무중단)
```

**관련 파일 경로**

| 항목 | 경로 |
|---|---|
| certbot renewal 설정 | `/etc/letsencrypt/renewal/re-cap.duckdns.org.conf` |
| 인증서/키 (실제 파일, archive) | `/etc/letsencrypt/archive/re-cap.duckdns.org/` |
| 인증서/키 (심링크, nginx가 참조) | `/etc/letsencrypt/live/re-cap.duckdns.org/fullchain.pem`, `privkey.pem` |
| webroot (ACME 챌린지 파일) | `/home/ubuntu/recap/certbot-webroot` (호스트) → `/var/www/certbot` (nginx 컨테이너) |
| nginx 설정 | `/home/ubuntu/recap/nginx/conf.d/default.conf` |
| docker compose 설정 | `/home/ubuntu/recap/docker-compose.yml` |
| certbot 자동 실행 타이머 | systemd `certbot.timer` / `certbot.service` |
| certbot 로그 | `/var/log/letsencrypt/letsencrypt.log` |

**renewal 설정 (`/etc/letsencrypt/renewal/re-cap.duckdns.org.conf`, 비밀값 제외)**

```
[renewalparams]
server = https://acme-v02.api.letsencrypt.org/directory
key_type = ecdsa
authenticator = webroot
deploy_hook = docker exec recap-nginx nginx -s reload

[[webroot_map]]
re-cap.duckdns.org = /home/ubuntu/recap/certbot-webroot
```

**nginx 80번 서버블록 (`nginx/conf.d/default.conf`)**

```nginx
server {
    listen 80;
    server_name re-cap.duckdns.org;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}
```

**docker-compose.yml nginx 볼륨**

```yaml
  nginx:
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d
      - /etc/letsencrypt:/etc/letsencrypt:ro
      - /home/ubuntu/recap/certbot-webroot:/var/www/certbot
```

**certbot.timer 스케줄**

```
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=43200
```

하루 2회(00:00, 12:00 UTC) 트리거되고, 매 트리거마다 최대 12시간의 랜덤 지연이 추가된다.
(비대화형 실행 시 certbot 자체에도 별도의 랜덤 지연이 한 번 더 들어간다 — 5장 참고)

### 3. 장애 이력 (2026-10-08)

- **증상**: iOS/Android 앱에서 로그인 실패. 서버(nginx/app) 로그에는 `/api/v1/auth/*` 요청 자체가 들어오지 않음 (스캐너/봇 트래픽은 정상적으로 계속 유입).
- **원인**:
  - 인증서가 **2026-09-24 09:31:38 UTC**에 만료됨
  - 당시 갱신 방식이 `authenticator = standalone`이었는데, 80번 포트를 이미 `recap-nginx` 컨테이너가 점유 중이라 certbot이 포트를 바인드하지 못해 매 갱신 시도마다 `StandaloneBindError: Problem binding to port 80: [Errno 98] Address already in use` 발생
  - `certbot.timer`는 하루 2회 정상적으로 실행되고 있었지만 매번 이 에러로 실패 — 적어도 인증서 생성 시점(2026-06-26)부터 약 3개월간 갱신이 한 번도 성공하지 못한 상태였음
  - iOS(CFNetwork)/Android(okhttp)는 TLS 인증서 만료를 엄격히 검증해 핸드셰이크 단계에서 연결을 거부했고, 다수의 스캐너/봇은 인증서 검증을 하지 않거나 평문 요청이라 영향을 받지 않아 서버가 "살아있는 것처럼" 보였음
- **복구**:
  - STAGE 1 (즉시 복구): `sudo certbot renew --pre-hook "docker stop recap-nginx" --post-hook "docker start recap-nginx"` 로 80번 포트를 잠깐 비워 standalone 챌린지를 통과시키고 수동 발급 (다운타임 약 10~20초)
  - STAGE 2 (재발 방지): 갱신 방식을 `standalone` → `webroot`로 전환, `deploy_hook`으로 `nginx -s reload` 자동화 (2장 구조)
- **인지 지연**: 만료 후 약 2주간 아무도 인지하지 못함 — 만료/갱신 실패에 대한 모니터링/알림이 없었기 때문 (7장 후속 과제 참고)

### 4. 운영 가이드

```bash
# 현재 인증서 상태 확인 (만료일, 경로)
sudo certbot certificates

# 갱신 시뮬레이션 (실제 발급 아님, rate limit 영향 적음)
sudo certbot renew --dry-run

# 수동 갱신 (만료 30일 이내일 때만 실제로 갱신 시도함)
sudo certbot renew

# certbot 로그 확인
tail -n 100 /var/log/letsencrypt/letsencrypt.log
journalctl -u certbot.service -n 100

# 실제 서빙 중인 인증서의 만료일 확인 (서버 설정과 무관하게 외부에서 직접 확인)
echo | openssl s_client -connect re-cap.duckdns.org:443 2>/dev/null | openssl x509 -noout -dates
```

### 5. 장애 대응 체크리스트 (webroot 구조 기준)

| 증상 | 확인 명령 | 다음 조치 |
|---|---|---|
| 앱 로그인/API 호출 실패, nginx 로그에 해당 요청 없음 | `echo | openssl s_client -connect re-cap.duckdns.org:443 2>/dev/null \| openssl x509 -noout -dates` | `notAfter`가 과거 날짜면 인증서 만료 → 아래로 |
| 인증서 만료 확인됨 | `sudo certbot certificates` | `INVALID: EXPIRED` 확인 |
| 갱신 자체가 실패하고 있는지 확인 | `sudo tail -n 60 /var/log/letsencrypt/letsencrypt.log` | `StandaloneBindError` 등 에러 패턴 확인 |
| webroot 챌린지 경로가 실제로 동작하는지 확인 | `mkdir -p /home/ubuntu/recap/certbot-webroot/.well-known/acme-challenge && echo test > .../ping` 후 `curl -s http://re-cap.duckdns.org/.well-known/acme-challenge/ping` | `test` 응답 안 오면 nginx 설정/볼륨 마운트 깨짐 — 2장 구조와 비교 |
| 수동 복구 | `sudo certbot renew --dry-run` 먼저 (rate limit 걱정 없이 검증) → 성공 시 `sudo certbot renew` | `deploy_hook`이 자동으로 `nginx -s reload` 실행 (다운타임 없음) |
| 복구 후 | `docker ps`, `sudo certbot certificates`, `curl -sI https://re-cap.duckdns.org/api/v1/app/version-check` | 각각 Up / 새 만료일 / 200 확인 |

### 6. 롤백 (STAGE 1/2 변경 전 상태로 복원)

2026-10-08 작업 당시 생성된 백업 파일:

| 백업 파일 | 원본 경로 | 복원 명령 |
|---|---|---|
| `/etc/letsencrypt.bak.20261008093949` | `/etc/letsencrypt` (전체) | `sudo rm -rf /etc/letsencrypt && sudo cp -a /etc/letsencrypt.bak.20261008093949 /etc/letsencrypt` |
| `/etc/letsencrypt/renewal/re-cap.duckdns.org.conf.bak.20261008094851` | `/etc/letsencrypt/renewal/re-cap.duckdns.org.conf` | `sudo cp -a <백업> /etc/letsencrypt/renewal/re-cap.duckdns.org.conf` |
| `/home/ubuntu/recap/docker-compose.yml.bak.20261008094851` | `/home/ubuntu/recap/docker-compose.yml` | `cp -a <백업> /home/ubuntu/recap/docker-compose.yml && cd /home/ubuntu/recap && docker compose up -d nginx` |
| `/home/ubuntu/recap/nginx/conf.d/default.conf.bak.20261008094851` | `/home/ubuntu/recap/nginx/conf.d/default.conf` | `cp -a <백업> /home/ubuntu/recap/nginx/conf.d/default.conf && docker exec recap-nginx nginx -s reload` |

참고로 `docker-compose.yml.bak.20260811082412`는 이번 작업과 무관한 이전 백업(2026-08-11)이다.

### 7. 후속 과제

- [ ] UptimeRobot 등으로 SSL 인증서 만료 알림 설정 (만료 14~30일 전 경고)
- [ ] certbot 갱신 실패 시 알림 연동 (예: `deploy_hook`/`renew_hook` 실패 감지, 또는 별도 헬스체크 배치)
- [ ] 2026-12-07 전후로 실제 자동 갱신이 webroot 방식으로 정상 동작하는지 확인 (만료 30일 전 시점)
- [ ] 누적된 `*.bak.*` 백업 파일 정리 (복구 안정성 확인 후)
- [ ] (선택) ACM + ALB로 전환해 인증서 갱신을 AWS 관리형으로 이전하는 것 검토

### 8. 주의사항

- Let's Encrypt rate limit: 실패한 검증(Failed Validation)은 **동일 호스트명 기준 시간당 5회**로 제한된다. 갱신이 실패했다고 반복 재시도하지 말 것 — 원인을 먼저 파악하고 조치한 뒤 재시도한다.
- `--dry-run`은 Let's Encrypt의 **staging 서버**를 사용하므로 실제 인증서 발급/rate limit에 영향을 주지 않는다. 구조 변경 후 검증은 항상 `--dry-run`을 먼저 사용한다.
