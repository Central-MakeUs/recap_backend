## EC2 원격 접근 규칙

Claude Code는 `ssh recap-prod`로 운영 서버에 직접 접근할 수 있다.
다만 명령의 성격에 따라 다르게 처리한다.

### 자율 실행 가능 (확인 없이 바로 실행)
서버 상태를 전혀 바꾸지 않는, 순수 조회성 명령만 해당한다.
- 로그 확인 (docker logs, tail, grep 등)
- 상태 확인 (docker ps, systemctl status, df -h 등)
- 파일 내용 읽기 (cat, docker inspect 등)

### 반드시 사용자 확인 후 실행 (계획만 먼저 보고)
서버 상태를 바꾸거나, 되돌리기 어려운 모든 명령.
- 컨테이너 재시작/재생성 (docker compose up/down/restart)
- 설정 파일 수정 (docker-compose.yml, .env, logrotate 설정 등)
- 파일/디렉터리 삭제
- DB에 직접 접속해서 실행하는 모든 쿼리(SELECT 포함 — 운영 DB
  접속 자체를 사용자 승인 후로 통일)

이 구분이 애매한 명령이면, 자율 실행 판단을 스스로 내리지 말고
반드시 계획을 먼저 보고하고 확인받는다.
