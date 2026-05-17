# Kotlin Migration Harness

Prithvi Rajasekaran 의 *"Effective harnesses for long-running agents"* 글에서 소개된
3-agent GAN-inspired harness 구조를, **Java → Kotlin 마이그레이션** 작업에 맞게
조정한 뒤, 마이그레이션 완료 후 학습·요약을 자동 산출하는
**Retrospective 에이전트**를 한 칸 더 붙인 3+1 구성입니다.

원본 글의 핵심 통찰 4가지를 그대로 유지하면서, **메인 세션이 오케스트레이터
역할을 맡아 Planner→Generator↔Evaluator 루프를 `/harness` 한 번으로 자동
실행**하도록 확장했습니다. 모든 코드 변경은 **격리된 git worktree
(`.claude/worktrees/harness/kotlin-migration`)** 안에서만 수행되므로 사용자의
메인 체크아웃을 오염시키지 않습니다.

1. **태스크 분해(decomposition)**: 큰 작업을 sprint 단위로 쪼개 generator 가
   한 번에 한 chunk 씩만 다루게 한다.
2. **Generator ↔ Evaluator 분리**: self-evaluation 의 관대함 문제(generator 가
   자기 결과를 무비판적으로 통과시키는 현상)를 회피한다.
3. **Sprint contract**: 각 sprint 시작 전 "done" 의 정의를 generator 와
   evaluator 가 협상해 명문화한다 → 산출물이 spec 과 contract 양쪽에 묶인다.
4. **파일 기반 핸드오프(file-based handoff)**: 모든 에이전트 간 통신은
   `.claude/harness/workspace/` 의 markdown 파일로만 이루어진다. context reset
   에 강하고, 사용자가 사후에 감사하기 쉽다.

추가된 자동화 통찰:

5. **단일 명령 진입점**: `/harness <한 줄 의도>` 로 시작 → spec→sprint 루프→
   최종 빌드까지 메인 세션이 sub-agent 를 순차 호출하며 끝까지 진행.
6. **머신 파싱 가능한 STATUS 라인**: contract / review 파일 첫 줄이 정확한
   STATUS 문자열이어야 다음 단계로 전환. 텍스트 형식 자유는 본문에만 허용.
7. **worktree 격리**: 사용자의 main 체크아웃은 절대 수정되지 않음. 모든 sprint
   커밋은 `harness/kotlin-migration` 브랜치에만 쌓이고, 머지는 사용자 결정.

---

## 디렉토리 레이아웃

```
.claude/
├── commands/
│   └── harness.md                  # /harness 슬래시 명령 진입점
├── harness/
│   ├── README.md                   # 이 문서
│   ├── orchestrator.md             # 오케스트레이터 런북 (자동화 권위문서)
│   ├── agents/
│   │   ├── planner.md              # Planner system prompt
│   │   ├── generator.md            # Generator system prompt
│   │   ├── evaluator.md            # Evaluator system prompt
│   │   └── retrospective.md        # Retrospective system prompt (Phase L + W)
│   ├── criteria/
│   │   └── kotlin-conversion.md    # 평가 기준 + few-shot 예시
│   └── workspace/                  # 에이전트 간 통신 채널
│       ├── spec/                   # Planner → Generator (+ Evaluator)
│       │   └── product-spec.md
│       ├── contracts/              # Generator ↔ Evaluator (협상)
│       │   └── sprint-NN-contract.md
│       ├── handoffs/               # Generator → Evaluator
│       │   └── sprint-NN-handoff.md
│       ├── reviews/                # Evaluator → Generator
│       │   └── sprint-NN-review.md
│       ├── logs/                   # 누적 실행 로그 (오케스트레이터가 append)
│       │   └── run-log.md
│       ├── learnings.md            # Retrospective Phase L 산출물 (마이그레이션 1회 = 1 파일)
│       └── wrap-up.md              # Retrospective Phase W 산출물 (마이그레이션 1회 = 1 파일)
├── skills/                         # 자동 발견되는 skill 들
│   ├── harness-status/SKILL.md     # 현재 sprint 진행 상황 요약
│   └── kotlin-conversion-context/SKILL.md  # 헥사고날 + Kotlin 컨버전 컨텍스트
└── settings.json                   # hooks 정의 (SessionStart 등)
```

## 에이전트 역할 요약

| Agent | Input | Output | 주 책임 |
|-------|-------|--------|--------|
| **Planner** | 1-4 문장의 사용자 의도 | `workspace/spec/product-spec.md` | 사용자 한 줄 의도를 sprint 로 분해된 마이그레이션 spec 으로 확장. 스코프는 야심차게, 기술 디테일은 generator 에게 위임. |
| **Generator** | spec + 직전 review | sprint contract 제안 → 코드 변환 → handoff | 한 sprint 의 파일 묶음만 Kotlin 으로 변환. `./gradlew test` 와 ArchUnit 으로 self-check 후 handoff. |
| **Evaluator** | handoff + spec + contract | review (PASS/FAIL + 구체적 결함 목록) | contract 의 모든 항목을 실행/검증. 기준 4가지를 점수화. 하나라도 임계값 미달이면 sprint FAIL. |
| **Retrospective** | 모든 handoff + review + run-log + commit history | `workspace/learnings.md`, `workspace/wrap-up.md` | 마지막 sprint PASS 이후 1회만. Phase L 에서 cross-sprint 학습을 추출하고, Phase W 에서 1-페이지 wrap-up 을 작성. 코드 편집은 하지 않음. |

## 한 sprint 의 표준 흐름

```
              ┌──────────┐
   user 의도 → │ Planner  │ → product-spec.md (1회만)
              └──────────┘
                                ┌──────────────────────────────┐
              ┌──────────┐      │   sprint N (반복)            │
   spec   ─→  │ Generator│──┐   │                              │
              └──────────┘  │   │   1. Generator 가 contract 초안 작성
                            │   │   2. Evaluator 가 contract 검토 → 합의
                            ↓   │   3. Generator 가 코드 변환 + self-check
              ┌──────────┐  │   │   4. Generator → handoff.md
   contract ↔ │Evaluator │← ┘   │   5. Evaluator 가 contract 항목 실행 검증
              └──────────┘      │   6. Evaluator → review.md (PASS / FAIL)
                                │   7. FAIL 이면 같은 sprint 재실행
                                │      PASS 면 sprint N+1 로 이동
                                └──────────────────────────────┘
                                              │
                                              ▼ (모든 sprint PASS 이후 1회)
                                  ┌───────────────────┐
                                  │  Retrospective    │ → learnings.md (Phase L)
                                  │  (3+1 단계)       │ → wrap-up.md   (Phase W)
                                  └───────────────────┘
```

## 평가 기준 (criteria/kotlin-conversion.md 참조)

원본 글의 frontend 4기준(design quality / originality / craft / functionality)을
**코드 변환 작업의 검증 가능한 4기준**으로 치환했습니다:

| 기준 | 가중치 | 임계값 (이하면 sprint FAIL) |
|------|-------|--------------------------|
| **Behavioral Correctness** | 35% | 모든 기존 테스트 PASS. ArchUnit 규칙 PASS. |
| **Idiomatic Kotlin** | 30% | data class / null safety / val / 확장 함수 등이 자연스럽게 적용됨. Lombok 제거. |
| **Architectural Integrity** | 20% | 헥사고날 패키지 경계와 포트/어댑터 구조가 보존됨. |
| **Code Quality** | 15% | Kotlin 컴파일러 경고 0. 일관된 네이밍/포맷. 불필요한 `!!`, `Any?` 남용 없음. |

## 사용 방법

### 자동화 모드 (권장) — `/harness`

```
/harness 모든 Java 소스를 Kotlin 으로 변환하되, 헥사고날 패키지 경계와 모든
        기존 테스트를 보존해줘.
```

이 한 줄로 메인 세션이 오케스트레이터 역할을 시작합니다. 진행 순서:

1. **Pre-flight**: cwd / git 상태 / 작업 트리 깨끗함 검사. 깨끗하지 않으면
   `needs input:` 으로 정지.
2. **Worktree 격리**: `EnterWorktree` tool 로 `harness/kotlin-migration`
   브랜치의 worktree 진입. 이후 모든 sub-agent 호출은 이 worktree 안에서 동작.
3. **Planner (1회)**: `agents/planner.md` 를 system prompt 로 한 sub-agent
   호출. 산출물 `workspace/spec/product-spec.md`. 끝부분의 `## Sprint Index`
   섹션 (`^- sprint-(\d{2}): (.+)$`) 으로 sprint 목록 파싱.
4. **Sprint 루프** (각 sprint N 마다):
   1. Generator 가 contract 초안 (`contracts/sprint-NN-contract.md`).
   2. Evaluator 가 검토. 첫 줄 STATUS 가 `AGREED` 면 다음, `NEEDS_REVISION`
      이면 Generator 재호출 (최대 3회 협상).
   3. Generator 가 코드 변환 + `./gradlew compileKotlin compileTestKotlin
      test check` self-check + handoff 작성.
   4. Evaluator 가 동일 명령을 직접 재실행하고 `reviews/sprint-NN-review.md`
      작성. 첫 줄 STATUS 가 `PASS` 면 다음, `FAIL` 이면 Generator 재호출
      (최대 3회 재시도).
   5. PASS 시 오케스트레이터가 `git add` + `git commit -m
      "feat(kotlin): sprint NN — <handoff 요약>"` 실행. Generator/Evaluator
      는 commit 하지 않음.
5. **Final verification**: `./gradlew clean build check` 통과 확인.
6. **Retrospective (Phase L)**: `agents/retrospective.md` 를 system prompt
   로 한 sub-agent 호출. 모든 review / handoff / run-log / commit history
   를 읽고 `workspace/learnings.md` 에 cross-sprint 학습 (패턴, 함정,
   Lombok→Kotlin 매핑, harness 자체에 대한 피드백, 미완료 후속작업) 을
   작성.
7. **Retrospective (Phase W)**: 같은 에이전트를 다시 호출해
   `workspace/wrap-up.md` 작성 (1-페이지 executive summary: 무엇을
   배포했나 / 스프린트 ledger / 최종 검증 결과 / 핵심 학습 5개 / 사용자
   다음 액션 체크리스트).
8. **Retrospective 커밋**: `chore(harness): record migration learnings
   and wrap-up` 으로 두 파일을 한 커밋에 묶음.
9. **보고**: worktree 경로, 브랜치명, sprint 별 + retrospective 커밋
   SHA, 신규 산출물 경로를 사용자에게 출력하고 종료. **자동 머지는 하지
   않음** — 머지는 사용자가 결정 (`wrap-up.md` §5 의 체크리스트 인용).

### 재개 모드

이미 한 번 실행했고 중간에 멈춘 경우, 인자 없이 `/harness` 만 입력하면
오케스트레이터가 `reviews/` 디렉토리를 스캔해 **가장 작은 미완료 sprint
번호** 부터 루프를 재개합니다. spec 이 없는 상태에서 인자 없이 호출하면
`needs input:` 으로 정지합니다.

모든 sprint 가 이미 PASS 인데 `workspace/learnings.md` 또는
`workspace/wrap-up.md` 가 비어 있는 경우, 재개 모드는 sprint 루프를
건너뛰고 곧바로 **Retrospective** 단계 (위 6→7→8) 만 실행합니다.

### 수동 모드 (fallback)

자동화가 실패했거나 한 단계만 다시 돌리고 싶을 때는 `agents/*.md` 의 내용을
그대로 system prompt 로 사용해 sub-agent 를 직접 호출할 수 있습니다.
`workspace/` 파일들이 모든 상태를 들고 있으므로 어느 단계에서든 이어 받을 수
있도록 설계되어 있습니다.

### 안전·격리 요약

- 메인 체크아웃은 절대 수정되지 않습니다. 모든 코드 변경은
  `.claude/worktrees/harness/kotlin-migration/` 안에서만 발생합니다.
- 자동 머지·자동 push 없음. Evaluator 가 PASS 한 sprint 만 커밋됩니다.
- 모든 sub-agent 는 `--no-verify` / `git reset --hard` / `git push --force`
  사용 금지. 오케스트레이터도 마찬가지입니다.
- 재시도 한도(기본 3회) 초과 시 silent 진행이 아니라 `needs input:` 으로
  정지하여 사용자에게 결정을 위임합니다.

자세한 내부 규약, Agent 호출 프롬프트 템플릿, 머신 파싱 정규식은
`orchestrator.md` 를 참조하세요.

## Skills (자동 발견)

`.claude/skills/` 의 두 skill 이 자동 발견됩니다:

| Skill | 트리거 |
|-------|--------|
| `harness-status` | "어디까지 됐어", "현재 sprint", "진행 상황" 등 진행도 질의 |
| `kotlin-conversion-context` | Java↔Kotlin 변환·리뷰 작업. Generator/Evaluator sub-agent 가 자동으로 로드해 헥사고날 레이아웃·Lombok 매핑·Money/Optional 함정을 즉시 인식 |

## Hooks (자동 실행)

`.claude/settings.json` 에 등록된 2개 훅이 harness 운영을 자동화합니다:

| 이벤트 | 스크립트 | 동작 |
|--------|---------|------|
| `SessionStart` (startup/resume/clear) | `scripts/session-start.sh` | spec 이 존재하면 sprint 진행 상황·다음 액션·worktree 위치를 세션 시작 시 자동 표시. 진행 중이 아니면 침묵. |
| `PostToolUse` (Write/Edit) | `scripts/log-workspace-write.sh` | `workspace/{spec,contracts,handoffs,reviews}/*.md` 가 수정될 때마다 타임스탬프 + 파일명 + 첫 줄 STATUS 를 `workspace/logs/run-log.md` 에 자동 append. |

훅은 harness 외 파일에 대해서는 침묵합니다 (early exit). 비활성화하려면
`.claude/settings.json` 에서 해당 항목을 지우거나 `settings.local.json` 으로
override 하세요.
