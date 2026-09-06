#!/usr/bin/env python3
"""
OCR 약 추출 벤치 — fixture 9종에서 파서 / LLM(gpt-4o) / 하이브리드 정확도를 표로 출력한다.

- 파서 결과: OcrParserRegressionTest(Java, 결정론적)를 한 번 돌려 그 리포트에서 뽑는다.
- LLM 결과: 이 스크립트가 직접 OpenAI를 호출한다(좌표 텍스트 방식, OpenAiClient와 동일).
- 하이브리드: isPrescription 라우팅(서식별 고유 문구)을 여기서 재현한다.

사용법:
  OPENAI_API_KEY=sk-... python3 scripts/ocr-bench.py
  OPENAI_API_KEY=sk-... OPENAI_MODEL=gpt-4o-mini python3 scripts/ocr-bench.py
  python3 scripts/ocr-bench.py            # 키 없으면 파서 열만

exactR = 정답(manifest)과 4개 필드(이름·1회량·1일횟수·총일수)가 완전히 일치한 약의 비율.
"""
import json
import os
import re
import subprocess
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FIX = f"{ROOT}/src/test/resources/ocr/fixtures"
MANIFEST = f"{ROOT}/src/test/resources/ocr/expected/manifest.json"
MODEL = os.environ.get("OPENAI_MODEL", "gpt-4o")

API_KEY = os.environ.get("OPENAI_API_KEY")
if not API_KEY and os.path.exists(f"{ROOT}/.env"):
    for line in open(f"{ROOT}/.env"):
        if line.startswith("OPENAI_API_KEY="):
            API_KEY = line.split("=", 1)[1].strip()

SYSTEM_PROMPT = """너는 한국 병원 처방전·약국 약봉투의 OCR 결과에서 복용할 약 목록을 뽑는 도우미다.
입력은 "텍스트 @(x,y)" 줄 목록이다. y 차이가 15 이내면 같은 행.
약 이름 오른쪽 같은 행의 한 자리 숫자를 x 작은 순서대로 [1회 투약량, 1일 투여횟수, 총 투약일수].
"1정씩2회5일분" 압축 표기는 그대로 파싱.
drugName: 제형 접미사(정/캡슐/서방정)는 이름의 일부 - 절대 떼지 마라. 용량 표기(mg)·괄호 성분명·제형만 나타내는 단어는 빼라. 성분명만 있는 줄은 약 아님.
dosagePerTime: 단위 포함 "1정"/"0.5정"/"5ml", 모르면 null. timesPerDay: 정수, 모르면 null.
totalDays: 정수. "교부일로부터 N일"은 총투약일수 아님. 모르면 null.
제외: 주의사항, 병원·약국명, 이름, 금액, 날짜, 보험코드(8~10자리).
아래 JSON 형식으로만: {"drugs":[{"drugName":"...","dosagePerTime":"...","timesPerDay":0,"totalDays":0}]}"""


def raw_text(fields):
    return "".join(f["inferText"] + ("\n" if f.get("lineBreak") else " ") for f in fields).strip()


def coord_text(fields):
    lines = []
    for f in fields:
        if not f.get("inferText", "").strip():
            continue
        vs = (f.get("boundingPoly") or {}).get("vertices", [])
        cx = int(sum(v["x"] for v in vs) / len(vs)) if vs else 0
        cy = int(sum(v["y"] for v in vs) / len(vs)) if vs else 0
        lines.append((cy, cx, f["inferText"].strip()))
    lines.sort()
    return "\n".join(f"{t} @({x},{y})" for y, x, t in lines)


_DRUG_SUFFIX = r"(?:정|캡슐|캅셀|시럽|액|연고|크림|주사|산|패치)"


def is_prescription(t):
    code = len(re.findall(r"\d{8,10}\s+[가-힣A-Za-z]", t)) >= 2
    compact = len(re.findall(r"\d+(?:\.\d+)?\s*(?:정|캡슐|캅셀|ml|mg|g|포)\s*씩\s*\d+\s*회\s*\d+\s*일분", t)) >= 2
    # 약봉투의 *약이름 — 별표 뒤 단어가 약 접미사로 끝나야 함 (Java STARRED_DRUG_NAME_PATTERN과 동일)
    star = re.search(r"\*[가-힣a-zA-Z][가-힣a-zA-Z0-9]*" + _DRUG_SUFFIX, t) is not None
    rx = code or any(k in t for k in ("처방전", "처방 의약품", "처방의약품", "교부번호", "교부일"))
    bag = ("복약안내" in t) or ("약봉투" in t) or star or compact
    receipt = any(k in t for k in ("약제비", "계산서", "본인부담금"))
    return rx and not bag and not receipt


def call_llm(fields):
    body = json.dumps({
        "model": MODEL, "temperature": 0,
        "response_format": {"type": "json_object"},
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": coord_text(fields)},
        ],
    }).encode()
    req = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions", data=body,
        headers={"Authorization": f"Bearer {API_KEY}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        content = json.loads(r.read())["choices"][0]["message"]["content"]
    drugs = json.loads(content).get("drugs", [])
    out = []
    for d in drugs:
        name = (d.get("drugName") or "").strip()
        if not name:
            continue
        tpd = d.get("timesPerDay")
        tot = d.get("totalDays")
        out.append((
            name,
            (d.get("dosagePerTime") or None),
            tpd if isinstance(tpd, int) and 1 <= tpd <= 6 else None,
            tot if isinstance(tot, int) and 1 <= tot <= 90 else None,
        ))
    return out


def norm(d):
    return (d["drugName"], d.get("dosagePerTime"), d.get("timesPerDay"), d.get("totalDays"))


def recall(expected, actual):
    if not expected:
        return 0.0
    aset = set(actual)
    hit = sum(1 for e in expected if norm(e) in aset)
    return hit / len(expected)


def parser_recalls():
    """OcrParserRegressionTest를 돌려 fixture별 exact R을 뽑는다."""
    subprocess.run(["./gradlew", "test", "--tests", "*OcrParserRegressionTest", "--rerun", "-q"],
                   cwd=ROOT, capture_output=True)
    res = {}
    for fn in os.listdir(f"{ROOT}/build/test-results/test"):
        if "OcrParserRegressionTest" not in fn:
            continue
        s = open(f"{ROOT}/build/test-results/test/{fn}").read()
        for cdata in re.findall(r"<system-out><!\[CDATA\[(.*?)\]\]></system-out>", s, re.S):
            for m in re.findall(r"\[([\w.]+\.json)\] exact P=[\d.]+ R=([\d.]+)", cdata):
                res[m[0]] = float(m[1])
    return res


def main():
    runs = int(os.environ.get("RUNS", "1"))
    manifest = json.load(open(MANIFEST))["fixtures"]
    fixtures = [fc for fc in manifest if os.path.exists(f"{FIX}/{fc['file']}")]
    print(f"fixture {len(fixtures)}종 · LLM={MODEL} · {runs} run 평균" if API_KEY else f"fixture {len(fixtures)}종 · 파서만 (키 없음)")

    p_rec = parser_recalls()

    W = 38
    print(f"\n{'fixture':<{W}}{'라우팅':<11}{'파서':>7}{'LLM':>7}{'하이브리드':>10}")
    print("-" * (W + 35))
    sp = sl = sh = 0.0
    for fc in fixtures:
        fields = json.load(open(f"{FIX}/{fc['file']}"))["images"][0]["fields"]
        exp = fc["expected"]
        route_parser = is_prescription(raw_text(fields))
        p = p_rec.get(fc["file"], float("nan"))

        if not API_KEY:
            route = "파서(처방전)" if route_parser else "LLM"
            print(f"{fc['file']:<{W}}{route:<11}{p:>7.2f}{'—':>7}{'—':>9}")
            continue

        ls = []
        for _ in range(runs):
            try:
                llm = call_llm(fields)
            except Exception as e:
                print(f"  ! {fc['file']} LLM 오류: {e}", file=sys.stderr)
                llm = []
            ls.append((recall(exp, llm), bool(llm)))
        l = sum(x[0] for x in ls) / runs
        any_drug = any(x[1] for x in ls)
        hybrid_val = p if route_parser else (l if any_drug else p)
        route = "파서(처방전)" if route_parser else ("LLM" if any_drug else "파서(폴백)")
        print(f"{fc['file']:<{W}}{route:<11}{p:>7.2f}{l:>7.2f}{hybrid_val:>9.2f}")
        sp += p; sl += l; sh += hybrid_val

    if API_KEY:
        n = len(fixtures)
        print("-" * (W + 35))
        print(f"{'평균':<{W}}{'':<11}{sp/n:>7.2f}{sl/n:>7.2f}{sh/n:>9.2f}")
    print("\nexactR = 정답과 4개 필드 완전 일치 비율. LLM은 temperature 0이어도 run마다 소폭 변동 (RUNS=3 로 평균 권장).")


if __name__ == "__main__":
    main()
