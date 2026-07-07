---
name: pr
description: RECAP 서버에서 GitHub PR을 생성하거나 갱신한다. 사용자가 "PR 만들어줘", "PR 올려줘" 등을 요청할 때 사용.
---

# pr

RECAP 서버의 Pull Request를 생성하거나 갱신한다. PR 본문은 실제 diff,
관련 이슈, ADR/LLD가 있으면 해당 문서를 기준으로 작성한다.

## 핵심 원칙

- 추측하지 않는다. 실제 diff, 이슈, 문서에 없는 내용은 쓰지 않는다.
- PR 본문은 **기존 `.github/PULL_REQUEST_TEMPLATE.md`를 그대로 사용한다.**
  mody-server의 자체 템플릿(개요/변경 사항/문서/비고 구조)을 새로 쓰지 않는다.
- PR 제목은 한글로 작성하되, 이슈 제목 접두어 규칙(`[FEAT]`, `[BUG]` 등)에
  대응해 `[유형]#이슈번호 제목` 형식을 쓴다 (예: `[Feature]#12 카카오 OAuth
  로그인 구현`, `[Docs]#8 하네스 문서 체계 도입`).
- PR 생성 전 `git status`, 현재 브랜치, 원격 push 상태를 확인한다.
- 이슈를 완전히 해결하는 PR이면 `close #N`, 일부만 다루면 `Refs #N`을
  본문의 "관련 이슈" 섹션에 쓴다.
- base 브랜치는 기본적으로 `main`이다.
- `.agents/`는 사용자가 명시하지 않으면 PR에 포함하지 않는다. `docs/policy/`
  는 커밋 정책상 자율 반영 대상이므로 포함해도 된다.
- PR 생성 또는 갱신 시 Assignee와 Label을 지정한다 (아래 "확인 필요" 참고).

## 확인 필요 — mody와 달라지는 지점

- 기본 Assignee: mody는 `msk226`으로 하드코딩되어 있었다. RECAP의 기본
  담당자는 아직 정해지지 않았으니, 확정 전까지는 **Assignee를 비워두지 말고
  사용자에게 매번 묻는다.**
- Label 목록: mody의 `labels.json`을 그대로 가져오지 않는다. RECAP 저장소의
  실제 라벨(`gh label list --limit 100`, 2026-07-08 확인 기준)은
  `Feature`/`Fix`/`Bug`/`Docs`/`Refactor`/`API`/`Chore`/`Setting`이다.
  목록이 바뀌었을 수 있으니 사용 전 다시 확인한다. `젤로`/`조이`/`예니`/
  `올리버`/`민물`은 담당자 태그용으로 보이며 분류 라벨이 아니다.

## 사전 확인

- 작업 브랜치는 `{type}#{issue-number}` 형식을 우선한다 (예: `feat#12`).
- 현재 브랜치가 `main`이면 PR을 만들지 말고 새 브랜치 필요 여부를 사용자에게
  확인한다.
- 아직 커밋되지 않은 변경이 있으면 PR 생성 전에 커밋 필요 여부를 확인한다.
- 원격에 브랜치가 없으면 `git push -u origin <branch>` 후 PR을 만든다.
- 관련 이슈가 있으면 이슈 label을 확인해 PR에도 같은 label을 붙인다.

## 절차

1. `git status --short --branch`와 `git log --oneline --decorate -5`로
   상태를 확인한다.
2. `gh issue view <N>`으로 관련 이슈를 읽는다.
3. base를 결정한다 (기본 `main`).
4. `git diff <base>...HEAD --stat`와 필요한 파일 diff를 읽는다.
5. 관련 ADR/LLD가 있으면 읽고, 없으면 diff와 이슈 기준으로만 쓴다.
6. `.github/PULL_REQUEST_TEMPLATE.md` 내용을 그대로 채워 본문을 작성한다.
7. `gh pr create --base <base> --head <branch> --title "<title>" --body-file <tmpfile>`로
   생성한다. Assignee/Label은 사용자에게 확인된 값으로 `--assignee`,
   `--label` 옵션에 추가한다.
8. 이미 PR이 있으면 `gh pr edit <PR>`로 갱신한다.
9. 생성/갱신 후 `gh pr view <PR> --json assignees,labels`로 반영 여부를
   확인한다.

## 제목 예시

```
[Feature]#12 카카오 OAuth 로그인 구현
[Fix]#15 Refresh Token 만료 검증 오류 수정
[Docs]#8 하네스 문서 체계 도입
```

## 하지 말 것

- 문서나 diff에 없는 설계/결정을 본문에 서술하기.
- 테스트를 안 했는데 통과했다고 쓰기.
- 사용자가 원하지 않은 변경 파일을 커밋/PR에 포함하기.
- `.github/PULL_REQUEST_TEMPLATE.md`를 무시하고 다른 구조로 작성하기.
- Assignee를 확인 없이 임의로 지정하기.
