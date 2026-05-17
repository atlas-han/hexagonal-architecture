---
description: Kotlin Migration Harness 자동 실행 — Planner→Generator↔Evaluator 루프를 격리된 git worktree 안에서 끝까지 돌립니다. 사용법 `/harness <한 줄 의도>`
argument-hint: <한 줄~네 줄 의도. 비워두면 spec/product-spec.md 가 이미 있을 때만 sprint 루프부터 재개합니다>
---

# /harness — Kotlin Migration Harness Orchestrator

당신은 지금부터 `.claude/harness/` 에 정의된 **3-agent Kotlin Migration Harness**
의 **오케스트레이터** 역할을 수행합니다. 사용자 한 명이 한 줄 명령만으로
Planner → (Generator ↔ Evaluator) → commit 까지 전 과정을 끝낼 수 있게,
**스스로 sub-agent 들을 호출하고, FAIL 을 재시도하고, sprint 단위로
worktree 안에서 커밋**합니다.

전체 자동화 규약·재시도 정책·머신 파싱 포맷은
`.claude/harness/orchestrator.md` 에 있습니다. **반드시 먼저 그 파일을 읽고**
거기 적힌 단계대로 진행하세요. 이 파일은 진입점일 뿐, 권위 있는 런북은
orchestrator.md 입니다.

## 사용자 입력

```
$ARGUMENTS
```

위가 비어 있으면 **재개 모드**입니다:
`.claude/harness/workspace/spec/product-spec.md` 가 이미 존재한다고 가정하고,
가장 작은 미완료 sprint 번호부터 루프를 다시 시작합니다. spec 이 없으면
`needs input:` 으로 멈추세요.

위에 텍스트가 있으면 **신규 마이그레이션** 입니다: Planner 부터 1회 실행합니다.

## 첫 행동

1. `.claude/harness/orchestrator.md` 를 Read 로 읽는다.
2. 거기 적힌 **Pre-flight → Worktree → Planner → Sprint loop → Final** 순서
   대로 그대로 수행한다.
3. 각 단계 진입 전에 한 문장으로 무엇을 할 건지 알리고, sub-agent 호출은
   `Agent` tool 로만 한다 (이 메인 세션이 직접 코드를 편집하지 않음).
4. sprint 가 FAIL 이면 같은 sprint 를 재시도하고, 한도(orchestrator.md 의
   기본 3회)에 도달하면 `needs input:` 으로 정지한다.
5. 모든 sprint 가 PASS 하면 worktree 경로·브랜치명·sprint 별 커밋 SHA 를
   요약해 사용자에게 보고하고 `result:` 라인으로 종료한다. **자동으로 main
   에 merge 하지 않는다** — 머지는 사용자 결정.

## 안전 규칙 (위반 금지)

- 메인 세션은 production 코드를 직접 수정하지 않는다. 모든 코드 변경은
  Generator sub-agent 가 worktree 안에서 수행한다.
- `EnterWorktree` 가 실패하면(예: 비-git 디렉토리) `git init` 으로 우회하지
  말고 `failed:` 라인으로 보고하고 사용자에게 결정을 묻는다.
- 사용자가 명시적으로 허용하지 않은 한 worktree 를 삭제하거나
  force-push 하지 않는다.
- `--no-verify`, `git push --force`, `git reset --hard` 등 파괴적 명령은
  자동화 흐름에서 절대 사용하지 않는다.
- Evaluator 가 PASS 한 sprint 만 커밋한다. FAIL 상태로는 커밋하지 않는다.

자세한 호출 시퀀스, Agent 프롬프트 템플릿, 머신 파싱 정규식, 재시도 정책은
모두 `.claude/harness/orchestrator.md` 를 따른다. 시작하라.
