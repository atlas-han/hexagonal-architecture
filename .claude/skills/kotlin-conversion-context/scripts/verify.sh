#!/usr/bin/env bash
# Kotlin Migration Verification Script
# Run from the project root (or worktree root).
# Each section can be run independently.

set -euo pipefail

echo "=== Kotlin Migration Verification ==="

# ── 1. Compile ─────────────────────────────────────────────────────────────
echo ""
echo "--- Compile ---"
./gradlew compileKotlin compileTestKotlin

# ── 2. Test ─────────────────────────────────────────────────────────────────
echo ""
echo "--- Tests (including ArchUnit) ---"
./gradlew test
./gradlew check

# ── 3. Lombok removed ────────────────────────────────────────────────────────
echo ""
echo "--- Lombok import check (expect: no output) ---"
grep -R "import lombok" src/main/kotlin src/test/kotlin 2>/dev/null && {
  echo "FAIL: Lombok imports found in Kotlin sources"
  exit 1
} || echo "PASS: no Lombok imports"

grep -R "lombok" src/main/kotlin src/test/kotlin 2>/dev/null && \
  echo "WARNING: 'lombok' still referenced in Kotlin sources (may be build config)"

# ── 4. Kotlin idiom checks ──────────────────────────────────────────────────
echo ""
echo "--- Kotlin idiom audit ---"
echo "lateinit var occurrences (each should have a justifying comment):"
grep -Rn "lateinit var" src/main/kotlin || echo "  (none)"

echo ""
echo "@Autowired occurrences (should be zero — use constructor injection):"
grep -Rn "@Autowired" src/main/kotlin || echo "  (none)"

echo ""
echo "Optional<T> occurrences (should be zero in kotlin sources — use T?):"
grep -Rn "Optional<" src/main/kotlin || echo "  (none)"

echo ""
echo "!! (non-null assertion) occurrences (each must have a comment):"
grep -Rn "!!" src/main/kotlin | grep -v "//.*!!" || echo "  (none)"

# ── 5. Package layout diff ──────────────────────────────────────────────────
echo ""
echo "--- Package layout diff (java vs kotlin under src/main) ---"
java_dirs=$(find src/main/java   -type d 2>/dev/null | sed 's|src/main/java/||'   | sort)
kotlin_dirs=$(find src/main/kotlin -type d 2>/dev/null | sed 's|src/main/kotlin/||' | sort)
diff <(echo "$java_dirs") <(echo "$kotlin_dirs") && \
  echo "PASS: package layouts match" || \
  echo "WARNING: layouts differ (expected during migration — verify intentional)"

# ── 6. Remaining Java files ─────────────────────────────────────────────────
echo ""
echo "--- Java files remaining in src/ ---"
find src -name "*.java" | sort || echo "  (none)"

echo ""
echo "=== Verification complete ==="
