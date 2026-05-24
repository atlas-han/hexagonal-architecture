# Orchestrator Runbook — Kotlin Migration Harness

이 파일은 `/harness` 슬래시 명령이 진입할 때 메인 세션이 따라야 하는
**권위 있는 자동화 런북**입니다. Planner / Generator / Evaluator 3개의
sub-agent 를 `Agent` tool 로 호출하면서, sprint 단위로 `git worktree` 안에서
변환·검증·커밋을 반복합니다.

오케스트레이터는 **메타 에이전트**입니다:

- production 코드를 직접 수정하지 않음 (모든 코드 변경은 Generator sub-agent
  가 worktree 안에서 수행).
- 워크스페이스 파일을 직접 작성하지 않음 (각 sub-agent 가 자기 산출물 작성).
- 오케스트레이터의 책임은 **호출 순서 보장 / 격리 / 재시도 / 커밋 / 보고**.

---

## 0. Pre-flight

1. cwd 가 프로젝트 루트(=`build.gradle` 가 보이는 디렉토리)인지 확인.
2. `git rev-parse --is-inside-work-tree` 로 git 저장소 여부 확인.
   - 아니면 즉시 `failed: not a git repository — /harness requires git for
     worktree isolation` 으로 종료. **`git init` 자동 실행 금지** (사용자 동의 필요).
3. 작업 트리가 깨끗한지 확인: `git status --porcelain` 출력이 비어야 함.
   - 더러우면 `needs input: working tree not clean — commit or stash first` 로 종료.
4. 인자 파싱:
   - 인자가 있으면 **신규 모드**: Planner 부터 실행.
   - 인자가 비어 있으면 **재개 모드**: `.claude/harness/workspace/spec/product-spec.md`
     가 존재해야 함. 없으면 `needs input:` 으로 종료.

## 1. Worktree 격리

`EnterWorktree` tool 을 다음 이름으로 호출:

```
name = "harness/kotlin-migration"
```

- 이미 같은 이름의 worktree 가 있으면 그 경로로 진입(재개 모드).
- 진입 후 cwd 는 `.claude/worktrees/harness/kotlin-migration/` 이 됨.
- 이후 모든 Bash / Read / Edit / Agent 호출은 이 worktree 안에서 작동.

`EnterWorktree` 가 실패하면 우회하지 말고 `failed:` 로 즉시 보고.

## 2. Planner (신규 모드에서만 1회)

`Agent` tool 호출:

```
subagent_type: general-purpose
description: "Planner — produce product-spec"
prompt: |
  당신은 .claude/harness/agents/planner.md 에 정의된 Planner 입니다.
  먼저 그 파일을 Read 로 읽고, 거기 적힌 규약을 그대로 따르세요.

  사용자 의도:
  <<<
  {{USER_INTENT}}
  >>>

  산출물: .claude/harness/workspace/spec/product-spec.md (한 파일만).
  프로덕션 코드를 절대 편집하지 마세요. 완료되면 spec 의 sprint 개수와
  파일 경로 한 줄을 출력하고 종료하세요.
```

완료 후 spec 파일을 Read 로 직접 확인. spec 마지막의
`## Sprint Index` 섹션을 파싱해 sprint 번호 리스트(예: `[0,1,2,...,9]`)를
얻는다 (포맷은 §6 참조).

## 3. Sprint 루프

`sprint_numbers` 의 작은 번호부터 순차 처리. **하나의 sprint 가 PASS 커밋되기
전까지 다음 sprint 로 넘어가지 않는다.**

각 sprint N 에 대해:

### 3.1 Generator — contract 초안 (Agent 호출 1)

```
subagent_type: general-purpose
description: "Generator sprint N — draft contract"
prompt: |
  당신은 .claude/harness/agents/generator.md 에 정의된 Generator 입니다.
  먼저 그 파일과 .claude/harness/workspace/spec/product-spec.md 를 Read.

  이번 호출에서는 **Phase 1 (Contract 초안 작성)** 만 수행하세요.
  - sprint 번호: N
  - 산출물: .claude/harness/workspace/contracts/sprint-NN-contract.md
  - 코드는 아직 수정하지 마세요.
  - 파일을 작성하고 종료.
```

### 3.2 Evaluator — contract 검토 (Agent 호출 2)

```
subagent_type: general-purpose
description: "Evaluator sprint N — review contract"
prompt: |
  당신은 .claude/harness/agents/evaluator.md 에 정의된 Evaluator 입니다.
  먼저 그 파일과 product-spec.md, sprint-NN-contract.md 를 Read.

  이번 호출에서는 **Phase A (Contract review)** 만 수행하세요.
  - contract 가 합격 기준에 부합하면 파일 최상단에 정확히 한 줄
    `STATUS: AGREED` 를 추가하고 종료.
  - 부족하면 `// EVALUATOR:` 코멘트로 인라인 피드백을 남기고 최상단에
    `STATUS: NEEDS_REVISION` 을 적은 뒤 종료.
```

오케스트레이터는 contract 파일 첫 줄을 Read 로 확인:
- `STATUS: AGREED` → 3.3 으로.
- `STATUS: NEEDS_REVISION` → 3.1 을 재실행하되, 이번엔 프롬프트에
  "이전 contract 의 `// EVALUATOR:` 피드백을 반영해 같은 파일을
  갱신하세요" 를 덧붙임. **최대 3 회까지 협상.** 그 이상이면
  `needs input: contract negotiation stuck on sprint NN`.

### 3.3 Generator — 구현 + handoff (Agent 호출 3)

```
subagent_type: general-purpose
description: "Generator sprint N — implement"
prompt: |
  당신은 generator.md 의 Generator 입니다. contract 는 합의 완료
  (sprint-NN-contract.md 의 `STATUS: AGREED`).

  이번 호출에서는 **Phase 2 (구현)** + **Phase 3 (self-check)** +
  **Phase 4 (handoff 작성)** 을 모두 수행하세요.

  - cwd 는 이미 worktree (.claude/worktrees/harness/kotlin-migration).
  - 코드 편집은 worktree 안에서만.
  - self-check 통과 전까지 handoff 를 쓰지 마세요.
  - **자동으로 git commit 하지 마세요** — 커밋은 오케스트레이터의 책임.
  - 산출물: .claude/harness/workspace/handoffs/sprint-NN-handoff.md
```

### 3.4 Evaluator — handoff 검증 (Agent 호출 4)

```
subagent_type: general-purpose
description: "Evaluator sprint N — verify handoff"
prompt: |
  당신은 evaluator.md 의 Evaluator 입니다.
  먼저 evaluator.md, product-spec.md, sprint-NN-contract.md,
  sprint-NN-handoff.md 를 Read.

  이번 호출에서는 **Phase B (Handoff review)** 를 수행하세요.
  - mandatory commands 를 모두 직접 실행 (self-check 출력 신뢰 금지).
  - 산출물: .claude/harness/workspace/reviews/sprint-NN-review.md
  - 파일 최상단에 정확히 한 줄로 `STATUS: PASS` 또는 `STATUS: FAIL`.
```

### 3.5 PASS/FAIL 분기

review 파일 첫 줄을 Read:

- **PASS** → 3.6 으로.
- **FAIL** → 같은 sprint 의 Generator 구현(3.3)을 재호출. 단,
  프롬프트에 다음을 추가: "직전 review (sprint-NN-review.md) 의 Bugs found
  표를 1개씩 처리하세요. 새 handoff 는 각 FAIL 항목을 명시적으로 언급해야
  합니다." 그 뒤 다시 3.4. **최대 3 회 재시도.** 초과 시
  `needs input: sprint NN failing 3 retries — see reviews/sprint-NN-review.md`.

### 3.6 Commit (오케스트레이터 수행)

worktree 안에서:

1. `git status --porcelain` 로 변경 파일 확인.
2. handoff 의 `## Commit` 섹션에서 1줄 요약을 추출 (없으면 sprint 목표를 사용).
3. workspace 변경(`.claude/harness/workspace/**`) + 코드 변경을 함께 스테이지:

```
git add src .claude/harness/workspace
```

(필요시 `build.gradle` 등 명시 경로 추가. `git add -A`/`.` 금지.)

4. 커밋:

```
git commit -m "feat(kotlin): sprint NN — <summary>"
```

- `--no-verify` 금지. 훅 실패 시 원인 진단 후 같은 sprint 재시도(3.3).
- 절대 amend 금지.

5. 커밋 SHA 를 `logs/run-log.md` 에 append (§5 참조).

## 4. Final verification

모든 sprint 가 PASS 커밋되면:

```
./gradlew clean
./gradlew build
./gradlew check
```

이 통과해야 함. 실패하면 `needs input: final build failed — see logs` 로 종료.

## 4.5 Learnings (Agent 호출)

Final verification 가 통과한 직후, **Retrospective agent — Phase L** 을 1회
호출해 cross-sprint 학습 산출물을 작성한다.

```
subagent_type: general-purpose
description: "Retrospective Phase L — learnings"
prompt: |
  당신은 .claude/harness/agents/retrospective.md 에 정의된 Retrospective
  agent 입니다. 먼저 그 파일을 Read 로 읽고, 거기 적힌 규약을 그대로
  따르세요.

  이번 호출은 **Phase L (Learnings)** 만 수행하세요.
  - cwd 는 이미 worktree (.claude/worktrees/harness/kotlin-migration).
  - 산출물: .claude/harness/workspace/learnings.md (한 파일).
  - 코드 편집 / git 명령 / commit 금지.
  - 완료되면 `learnings written: <path>` 한 줄을 출력하고 종료.
```

호출 후 오케스트레이터는 `learnings.md` 가 실제로 생겼는지 Read 로
확인한다. 누락되어 있거나 길이가 비정상적으로 짧으면 `needs input:
learnings.md missing or malformed` 로 정지한다.

## 4.6 Wrap-up (Agent 호출)

Learnings 가 작성되면, **Retrospective agent — Phase W** 를 1회 호출해
최종 요약 산출물을 작성한다.

```
subagent_type: general-purpose
description: "Retrospective Phase W — wrap-up"
prompt: |
  당신은 retrospective.md 의 Retrospective agent 입니다.
  Phase L 은 이미 끝났고 learnings.md 가 작성되어 있습니다.

  이번 호출은 **Phase W (Wrap-up)** 만 수행하세요.
  - 산출물: .claude/harness/workspace/wrap-up.md (한 파일).
  - learnings.md, spec/product-spec.md, logs/run-log.md 와
    `git log --oneline` 결과를 종합해 1-페이지 요약을 작성.
  - 코드 편집 / git 명령 / commit 금지.
  - 완료되면 `wrap-up written: <path>` 한 줄을 출력하고 종료.
```

호출 후 `wrap-up.md` 의 존재를 Read 로 확인. 누락이면 `needs input:
wrap-up.md missing` 로 정지.

## 4.7 Commit (오케스트레이터 수행)

learnings.md + wrap-up.md 두 파일을 한 커밋으로 묶는다:

```
git add .claude/harness/workspace/learnings.md \
        .claude/harness/workspace/wrap-up.md
git commit -m "chore(harness): record migration learnings and wrap-up"
```

- 이 단계의 커밋도 `--no-verify` / amend 금지.
- 훅 실패 시 원인 진단 후 동일하게 Phase L 또는 Phase W 부터 재시도.
  최대 2 회까지 재시도; 초과 시 `needs input:` 으로 정지.

커밋 SHA 를 `logs/run-log.md` 에 append (§5 포맷). phase 는 `retrospective`.

## 4.8 Final report (사용자에게)

마지막으로 사용자에게 다음을 보고:

- worktree 경로 (`.claude/worktrees/harness/kotlin-migration`)
- 브랜치명 (`harness/kotlin-migration`)
- sprint 별 커밋 SHA + 한 줄 요약
- 신규 산출물 경로: `workspace/learnings.md`, `workspace/wrap-up.md`
- 머지/푸시 다음 단계 제안 (오케스트레이터가 직접 머지하지 않음).
  `wrap-up.md` §5 의 체크리스트를 그대로 인용해도 좋다.

그 후 `result: kotlin migration complete — N sprints + retrospective,
branch harness/kotlin-migration ready for review` 로 종료.

## 5. 누적 로그

매 sprint 전이 시점(시작, contract AGREED, handoff 작성, review PASS/FAIL,
commit) 에 `.claude/harness/workspace/logs/run-log.md` 에 한 줄을 append:

```
<ISO-8601 UTC> | sprint-NN | <phase> | <one-line note>
```

로그 작성은 오케스트레이터가 직접 Write/Edit 으로 수행해도 됨 (sub-agent
거치지 않음 — production 코드가 아님).

## 6. spec 의 sprint index 포맷 (Planner 와의 계약)

`product-spec.md` 의 **마지막 섹션**은 정확히 다음 포맷이어야 한다. 오케스트레이터는
이 섹션만 파싱하면 sprint 리스트를 얻을 수 있다.

```
## Sprint Index

- sprint-00: <one-line title>
- sprint-01: <one-line title>
...
- sprint-09: <one-line title>
```

번호는 zero-padded 2자리. 라인 정규식: `^- sprint-(\d{2}): (.+)$`.
이 포맷이 깨져 있으면 즉시 `needs input: spec sprint index malformed` 로 종료.

## 7. 안전·금지 규칙

- 오케스트레이터는 production 코드를 직접 편집·삭제하지 않는다.
- 오케스트레이터는 sub-agent 의 출력 텍스트만 신뢰하지 말고, 항상 **산출물
  파일의 상태**(존재, 첫 줄 STATUS) 로 다음 단계를 결정한다.
- **sprint 간 순차 의존 규칙**: 한 sprint 가 PASS 커밋되기 전까지 다음 sprint
  를 시작하지 않는다. Generator / Evaluator 를 sprint 간에 병렬 실행하지 않는다.
- worktree 를 자동 삭제·force-push·reset --hard 하지 않는다.
- 사용자가 명시적으로 허락하지 않은 한 원격에 push 하지 않는다.
- 최대 재시도 횟수(기본 3) 를 넘기면 silent 진행 금지. 반드시 `needs input:`
  으로 정지하고 어떤 sprint·어떤 phase 에서 막혔는지 알린다.

## 9. 병렬 처리 규칙 (Agent teams)

자세한 규칙은 `.claude/skills/parallel-agent/RULES.md` 를 참조.
오케스트레이터가 적용하는 핵심 규칙은 다음과 같다.

### 9.1 허용되는 병렬 실행

**A. Pre-flight 파일 읽기**

§0 + §2 진입 시 필요한 여러 파일을 한 메시지에서 병렬 Read 로 읽는다:

```
# 한 메시지에 동시 Read 호출
Read(spec/product-spec.md)
Read(reviews/sprint-NN-review.md)   # 마지막 완료 sprint
Read(logs/run-log.md)
```

**B. Evaluator 병렬 검증 (§3.4)**

contract 의 `Acceptance checks` 를 독립 그룹으로 묶어 병렬 Agent 로 분산:

```
# compile 먼저 (다른 체크의 전제)
Agent("eval-compile") → ./gradlew compileKotlin compileTestKotlin

# compile PASS 후 병렬 실행
Agent("eval-tests",    run_in_background=True) → ./gradlew test
Agent("eval-archunit", run_in_background=True) → ./gradlew check
Agent("eval-lombok",   run_in_background=True) → grep -R "import lombok" ...
Agent("eval-idioms",   run_in_background=True) → @Autowired, Optional<, !! scans
```

모든 병렬 에이전트가 완료된 후 결과를 합산해 `sprint-NN-review.md` 를 작성.
하나라도 FAIL 이면 전체 sprint FAIL.

**C. Final verification 병렬화 (§4)**

```
Sequential: ./gradlew clean
Parallel after clean:
  Bash("./gradlew build")
  Bash("./gradlew check")
  Bash("grep -R 'import lombok' src/")
```

**D. Retrospective 읽기 (§4.5 Phase L)**

Phase L 시작 시 모든 sprint review 파일을 병렬 Read 한 후 분석:

```
# 한 메시지에 모든 review를 병렬 읽기
Read(reviews/sprint-00-review.md)
Read(reviews/sprint-01-review.md)
...
Read(reviews/sprint-NN-review.md)
```

### 9.2 금지된 병렬 실행

| 금지 대상                         | 이유                                  |
|----------------------------------|---------------------------------------|
| Sprint N 과 Sprint N+1 동시 실행  | 빌드 의존성 — N+1 은 N 의 class 파일 필요 |
| 여러 Generator 동시 실행          | 같은 파일에 쓸 위험                    |
| 병렬 `git commit`                | git index 동시 접근 불가               |
| `./gradlew build` 동시 2개 이상  | Gradle 데몬 포트 충돌 가능             |

### 9.3 병렬 결과 수집 원칙

1. 병렬로 spawn 한 모든 Agent 가 완료될 때까지 다음 단계로 넘어가지 않는다.
2. 각 Agent 의 결과는 **산출물 파일**로 확인한다 (출력 텍스트만 신뢰하지 않음).
3. 타임아웃(10분): Agent 가 응답하지 않으면 FAIL 로 처리하고 `needs input:` 으로 정지.
4. 병렬 Agent 가 `run-log.md` 에 append 할 경우 타임스탬프 정렬로 읽는다.

## 8. 재개 모드 의사코드

```
read product-spec.md
parse sprint_numbers from "## Sprint Index"
for N in sprint_numbers:
    if reviews/sprint-NN-review.md exists and first line == "STATUS: PASS":
        continue                      # already done
    # this is the resume point
    start sprint N from §3.1 or, if contract already AGREED, §3.3
    break out of the resume search and proceed normally from §3

# 모든 sprint 가 이미 PASS 인 경우에도 종료가 아니라 retrospective 단계를 확인한다:
if all sprints PASS:
    if workspace/learnings.md missing:
        run §4.5 (Phase L)
    if workspace/wrap-up.md missing:
        run §4.6 (Phase W)
    if either was just written:
        run §4.7 (commit) and §4.8 (final report)
    else:
        report "all sprints + retrospective already complete" and exit
```

즉, **가장 작은 미완료 sprint 부터 시작**하고, sprint 가 전부 PASS 인
상태에서도 retrospective 산출물이 빠져 있으면 그 단계까지 마저
수행한다.
