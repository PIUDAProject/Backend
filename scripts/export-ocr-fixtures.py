#!/usr/bin/env python3
"""
ocr_result 50~53 행의 raw_response를 회귀 테스트 fixture로 내보낸다.
개인정보(이름·주민번호·전화·사업자번호·교부번호·의료기관·약사명)를 치환한다.

사용법:
  1. 아래 DB 접속 정보를 로컬 환경에 맞게 수정
  2. python3 export-ocr-fixtures.py
  3. 생성된 파일을 눈으로 검토 (개인정보 남았는지)
  4. git add src/test/resources/ocr/fixtures/*_real.json
"""
import json
import re
import subprocess

# --- DB 접속 (.env의 LOCAL_DB_* 값 사용. 비번은 MYSQL_PWD 환경변수로 전달) ---
import os
DB_NAME = "callcare"
MYSQL = ["mysql", "-uroot", DB_NAME, "-N", "--raw", "-e"]
_ENV = dict(os.environ)
try:
    for line in open(f"{os.path.dirname(__file__)}/../.env"):
        if line.startswith("LOCAL_DB_PASSWORD="):
            _ENV["MYSQL_PWD"] = line.split("=", 1)[1].strip()
except OSError:
    pass

REPO = "/Users/kangcheolung/cotato/callcare"
OUT = f"{REPO}/src/test/resources/ocr/fixtures"

MAPPING = {
    50: "pharmacy_receipt_grid_real.json",
    51: "pharmacy_receipt_scattered_real.json",
    52: "drug_bag_starred_real.json",
    53: "drug_bag_compact_real.json",
    54: "table_prescription_real.json",
}

# inferText 값에 적용할 치환 (개인정보 → 더미). 약 이름/용법은 건드리지 않음.
def anonymize(text: str) -> str:
    t = text
    # 주민번호(6-7 / 6-1 부분마스킹형), 만나이/생년
    t = re.sub(r"\d{6}\s*-\s*[1-8](\d{6})?", "******-*******", t)
    t = re.sub(r"\d{4}년생", "0000년생", t)
    t = re.sub(r"만\s*\d+세", "만 00세", t)
    # 전화번호
    t = re.sub(r"0\d{1,2}-?\d{3,4}-?\d{4}", "000-0000-0000", t)
    t = re.sub(r"\b01[016789]\d{7,8}\b", "01000000000", t)
    # 사업자등록번호
    t = re.sub(r"\b\d{3}-\d{2}-\d{5}\b", "000-00-00000", t)
    # 교부번호/영수증번호(숫자-숫자 또는 13자리)
    t = re.sub(r"\b\d{8,10}-\d{4,5}\b", "20250101-00000", t)
    t = re.sub(r"\b\d{13}\b", "2025010100000", t)
    t = re.sub(r"\b\d{12}\b", "000000000000", t)
    # 이름 (환자/약사/의사) — 실제 값으로 교체
    for name in ["신세인", "김필독", "방현조", "안인선"]:
        t = t.replace(name, "홍길동")
    # 의료기관/약국명
    for org, repl in [
        ("향촌 사랑 내과", "행복 내과"), ("향촌메디컬", "행복메디컬"),
        ("향 촌 우리약국", "행복 약국"), ("향 촌", "행복"), ("향촌", "행복"),
        ("우리약국", "행복약국"),
        ("터울병원", "행복병원"), ("필독약국", "행복약국"), ("필독", "행복"),
    ]:
        t = t.replace(org, repl)
    return t


def scrub_name_fields(fields):
    """'성명' 등 이름 라벨 뒤에 오는 짧은 한글 필드(약사/의사 이름 조각)를 마스킹한다."""
    LABELS = {"성명", "성 명", "환자 성명", "환 자 성 명", "조제한", "조제약사"}
    for i, f in enumerate(fields):
        if f.get("inferText", "").strip() in LABELS:
            for j in range(i + 1, min(i + 4, len(fields))):
                nxt = fields[j].get("inferText", "").strip()
                if 1 <= len(nxt) <= 3 and re.fullmatch(r"[가-힣]+", nxt):
                    fields[j]["inferText"] = "○" * len(nxt)


def process(row_id: int, filename: str):
    raw = subprocess.run(
        MYSQL + [f"SELECT raw_response FROM ocr_result WHERE ocr_result_id={row_id}"],
        capture_output=True, text=True, check=True, env=_ENV,
    ).stdout.strip()
    if not raw:
        print(f"  #{row_id}: raw_response 비어있음 — 스킵")
        return
    doc = json.loads(raw)
    for img in doc.get("images", []):
        for f in img.get("fields", []):
            if "inferText" in f:
                f["inferText"] = anonymize(f["inferText"])
        scrub_name_fields(img.get("fields", []))

    # 파서가 쓰는 필드만 남기고 축소 (inferConfidence, type 등 제거) + minify
    slim = {
        "version": doc.get("version", "V2"),
        "images": [{
            "inferResult": img.get("inferResult", "SUCCESS"),
            "fields": [{
                "inferText": f.get("inferText", ""),
                "lineBreak": bool(f.get("lineBreak", False)),
                "boundingPoly": {"vertices": [
                    {"x": v.get("x"), "y": v.get("y")}
                    for v in (f.get("boundingPoly") or {}).get("vertices", [])
                ]},
            } for f in img.get("fields", [])],
        } for img in doc.get("images", [])],
    }
    out_path = f"{OUT}/{filename}"
    with open(out_path, "w") as fp:
        json.dump(slim, fp, ensure_ascii=False, separators=(",", ":"))
        fp.write("\n")
    n = sum(len(img["fields"]) for img in slim["images"])
    print(f"  #{row_id} -> {filename}  ({n} fields)")


if __name__ == "__main__":
    for rid, fn in MAPPING.items():
        process(rid, fn)
    print("\n검토: grep -E '신세인|김필독|향촌|필독|터울|방현조|안인선' src/test/resources/ocr/fixtures/*_real.json")
