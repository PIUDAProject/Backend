#!/usr/bin/env bash
# OCR 추출 벤치 — 파서 vs LLM vs 하이브리드 정확도를 fixture 9종으로 측정해 표로 출력.
#
# 사용법:
#   OPENAI_API_KEY=sk-... scripts/ocr-bench.sh
#   OPENAI_API_KEY=sk-... OPENAI_MODEL=gpt-4o-mini scripts/ocr-bench.sh
#
# 키가 없으면 통합 테스트가 스킵되고 파서 단위 테스트 결과만 나온다.
set -euo pipefail
cd "$(dirname "$0")/.."

# .env에 OPENAI_API_KEY가 있으면 자동 로드
if [[ -z "${OPENAI_API_KEY:-}" && -f .env ]]; then
  export OPENAI_API_KEY="$(grep -E '^OPENAI_API_KEY=' .env | cut -d= -f2- || true)"
fi

echo "── 파서 회귀 테스트 (키 불필요) ──"
./gradlew test --tests "*OcrParserRegressionTest" --tests "*OcrCommandServiceTest" -q 2>&1 | tail -3 || true

if [[ -z "${OPENAI_API_KEY:-}" ]]; then
  echo
  echo "OPENAI_API_KEY 없음 → LLM 벤치 스킵. 키를 주면 파서 vs LLM vs 하이브리드 표가 나옵니다."
  exit 0
fi

echo
echo "── 파서 vs LLM vs 하이브리드 벤치 (${OPENAI_MODEL:-gpt-4o} 호출, ~\$0.15) ──"
./gradlew test --tests "*LlmDrugExtractorComparisonTest" -Dgroups=integration --rerun -q 2>&1 | tail -2 || true

# 테스트가 System.out으로 찍은 표를 결과 XML에서 뽑는다
python3 - <<'PY'
import glob, html, re
for f in glob.glob("build/test-results/test/*LlmDrugExtractorComparisonTest.xml"):
    s = open(f).read()
    for m in re.findall(r'<system-out><!\[CDATA\[(.*?)\]\]></system-out>', s, re.S):
        for line in html.unescape(m).splitlines():
            if "[Test worker]" in line or "spring-jcl" in line or "io.netty" in line:
                continue
            print(line)
PY
