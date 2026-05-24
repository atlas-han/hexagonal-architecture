# settings.json 적용 안내

이 파일은 `.claude/settings.json` 에 추가해야 할 hooks 내용을 설명합니다.
워크트리에서 settings.json 직접 수정이 차단되므로, 머지 후 수동으로 적용하세요.

## 적용 방법

`.claude/settings.json` 을 다음 내용으로 교체합니다:

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "hooks": {
    "SessionStart": [
      {
        "matcher": "startup|resume|clear",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/harness/scripts/session-start.sh"
          }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/harness/scripts/pre-bash-guard.sh"
          }
        ]
      },
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/harness/scripts/pre-edit-guard.sh"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/harness/scripts/log-workspace-write.sh"
          }
        ]
      },
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/harness/scripts/post-bash-build-log.sh"
          }
        ]
      }
    ],
    "Stop": [
      {
        "matcher": "",
        "hooks": [
          {
            "type": "command",
            "command": "${CLAUDE_PROJECT_DIR}/.claude/harness/scripts/session-end.sh"
          }
        ]
      }
    ]
  }
}
```

## 추가된 hooks 설명

| Hook | Trigger | Script | 역할 |
|------|---------|--------|------|
| `PreToolUse/Bash` | Bash 실행 전 | `pre-bash-guard.sh` | `force-push`, `reset --hard`, `--no-verify`, `clean -f` 차단 |
| `PreToolUse/Write\|Edit` | 파일 수정 전 | `pre-edit-guard.sh` | 워크트리 밖에서 `src/` 수정 시 경고 |
| `PostToolUse/Bash` | Bash 실행 후 | `post-bash-build-log.sh` | `./gradlew` 실패를 `run-log.md` 에 자동 기록 |
| `Stop` | 세션 종료 시 | `session-end.sh` | 미커밋 변경 경고 + 진행 상태 요약 |
