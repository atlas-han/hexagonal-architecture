# Kotlin Migration Harness

Prithvi Rajasekaran 의 *"Effective harnesses for long-running agents"* 글에서 소개된
3-agent GAN-inspired harness 구조를, **Java → Kotlin 마이그레이션** 작업에 맞게
조정한 구성입니다.

원본 글의 핵심 통찰 4가지를 그대로 유지합니다:

1. **태스크 분해(decomposition)**: 큰 작업을 sprint 단위로 쪼개 generator 가
   한 번에 한 chunk 씩만 다루게 한다.
2. **Generator ↔ Evaluator 분리**: self-evaluation 의 관대함 문제(generator 가
   자기 결과를 무비판적으로 통과시키는 현상)를 회피한다.
3. **Sprint contract**: 각 sprint 시작 전 "done" 의 정의를 generator 와
   evaluator 가 협상해 명문화한다 → 산출물이 spec 과 contract 양쪽에 묶인다.
4. **파일 기반 핸드오프(file-based handoff)**: 모든 에이전트 간 통신은
   `.claude/harness/workspace/` 의 markdown 파일로만 이루어진다. context reset
   에 강하고, 사용자가 사후에 감사하기 쉽다.

---

## 디렉토리 레이아웃

```
.claude/harness/
├── README.md                       # 이 문서
├── agents/
│   ├── planner.md                  # Planner system prompt
│   ├── generator.md                # Generator system prompt
│   └── evaluator.md                # Evaluator system prompt
├── criteria/
│   └── kotlin-conversion.md        # 평가 기준 + few-shot 예시
└── workspace/                      # 에이전트 간 통신 채널
    ├── spec/                       # Planner → Generator
    │   └── product-spec.md
    ├── contracts/                  # Generator ↔ Evaluator (협상)
    │   └── sprint-NN-contract.md
    ├── handoffs/                   # Generator → Evaluator
    │   └── sprint-NN-handoff.md
    ├── reviews/                    # Evaluator → Generator
    │   └── sprint-NN-review.md
    └── logs/                       # 누적 실행 로그
        └── run-log.md
```

## 에이전트 역할 요약

| Agent | Input | Output | 주 책임 |
|-------|-------|--------|--------|
| **Planner** | 1-4 문장의 사용자 의도 | `workspace/spec/product-spec.md` | 사용자 한 줄 의도를 sprint 로 분해된 마이그레이션 spec 으로 확장. 스코프는 야심차게, 기술 디테일은 generator 에게 위임. |
| **Generator** | spec + 직전 review | sprint contract 제안 → 코드 변환 → handoff | 한 sprint 의 파일 묶음만 Kotlin 으로 변환. `./gradlew test` 와 ArchUnit 으로 self-check 후 handoff. |
| **Evaluator** | handoff + spec + contract | review (PASS/FAIL + 구체적 결함 목록) | contract 의 모든 항목을 실행/검증. 기준 4가지를 점수화. 하나라도 임계값 미달이면 sprint FAIL. |

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

각 에이전트는 별도 Claude 세션에서 실행하거나, 메인 세션에서 `Agent` tool 로
호출할 수 있습니다. 호출 시 해당 `agents/*.md` 의 내용을 그대로 system prompt /
prompt 로 전달하면 됩니다.

사용자가 Kotlin 변환을 요청하면:

1. **Planner 실행 1회** → `workspace/spec/product-spec.md` 가 생성됨.
2. spec 의 첫 sprint 부터 차례로:
   1. Generator 가 `contracts/sprint-NN-contract.md` 초안 작성
   2. Evaluator 가 contract 를 검토/수정 → 최종 합의
   3. Generator 가 코드 변환 + `./gradlew test` 실행 → `handoffs/sprint-NN-handoff.md` 작성
   4. Evaluator 가 handoff 검증 → `reviews/sprint-NN-review.md` 작성
   5. PASS 면 commit (`feat(kotlin): sprint NN — <요약>`), FAIL 이면 generator 재실행

3. 모든 sprint PASS 후 최종 ArchUnit + `./gradlew build` 로 회귀 검증.
