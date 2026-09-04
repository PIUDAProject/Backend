#!/usr/bin/env python3
"""
약 검색 품질 before/after 벤치마크 (이슈 #92).

로컬 Elasticsearch의 `drug_info` 인덱스에서 문서를 뽑아 두 벤치 인덱스를 만든다.
  - drug_bench_old : 기존 매핑(itemName: text 기본 애널라이저), match_phrase_prefix
  - drug_bench_new : 신규 매핑(멀티필드 + 초성), bool.should 조합
라벨링 테스트셋으로 Recall@10 / MRR / latency 를 비교 출력한다.

사용:  앱을 한 번 띄워 drug_info 를 신규 매핑으로 재색인한 뒤
       python3 scripts/drug-search-bench.py
"""
import json
import statistics
import sys
import time
import urllib.request

ES = "http://localhost:9200"
SETTINGS_PATH = "src/main/resources/elasticsearch/drug-info-settings.json"

# (query, 정답 판별용 부분문자열, 유형)
TESTSET = [
    ("타이레놀", "타이레놀", "정확/시작"),
    ("타이레", "타이레놀", "시작"),
    ("타이레놀정", "타이레놀정", "정확/시작"),
    ("게보린", "게보린", "정확/시작"),
    ("판피린", "판피린", "시작"),
    ("겔포스", "겔포스", "시작"),
    ("이지엔", "이지엔", "시작"),
    ("훼스탈", "훼스탈", "시작"),
    ("부루펜", "부루펜", "시작"),
    ("까스활명수", "까스활명수", "정확/시작"),
    ("ㅌㅇㄹㄴ", "타이레놀", "초성"),
    ("ㄱㅂㄹ", "게보린", "초성"),
    ("ㅍㅍㄹ", "판피린", "초성"),
    ("ㄱㅍㅅ", "겔포스", "초성"),
    ("타이레올", "타이레놀", "끝오타"),
    ("게보른", "게보린", "짧은이름오타"),
    ("판피링", "판피린", "끝오타"),
    ("겔포수", "겔포스", "끝오타"),
    ("서방정", "서방정", "중간단어"),
    ("현탁액", "현탁액", "중간단어"),
    ("아세트아미노펜", "아세트아미노펜", "성분명"),
    ("이부프로펜", "이부프로펜", "성분명"),
    ("클로르페니라민", "클로르페니라민", "성분명"),
    ("마그네슘", "마그네슘", "성분명"),
]

OLD_MAPPING = {"properties": {"itemName": {"type": "text"}, "itemNameChosung": {"type": "text"}}}
NEW_MAPPING = {"properties": {
    "itemName": {"type": "text", "analyzer": "drug_search_analyzer", "fields": {
        "keyword": {"type": "keyword"},
        "autocomplete": {"type": "text", "analyzer": "drug_edge_ngram_analyzer",
                         "search_analyzer": "drug_search_analyzer"},
        "ngram": {"type": "text", "analyzer": "drug_ngram_analyzer",
                  "search_analyzer": "drug_ngram_analyzer"}}},
    "itemNameChosung": {"type": "text", "analyzer": "keyword", "search_analyzer": "keyword"}}}


def req(method, path, body=None):
    r = urllib.request.Request(
        ES + path, method=method,
        data=json.dumps(body).encode() if body is not None else None,
        headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(r))


def bulk_load(index, docs):
    lines = []
    for d in docs:
        lines.append('{"index":{}}')
        lines.append(json.dumps({"itemName": d["itemName"],
                                 "itemNameChosung": d.get("itemNameChosung", "")}, ensure_ascii=False))
    raw = ("\n".join(lines) + "\n").encode()
    r = urllib.request.Request(f"{ES}/{index}/_bulk?refresh", method="POST", data=raw,
                               headers={"Content-Type": "application/x-ndjson"})
    if json.load(urllib.request.urlopen(r)).get("errors"):
        print("bulk errors", file=sys.stderr)


def old_query(kw):
    return {"match_phrase_prefix": {"itemName": {"query": kw, "max_expansions": 50}}}


def new_query(kw):
    return {"bool": {"minimum_should_match": 1, "should": [
        {"term": {"itemName.keyword": {"value": kw, "boost": 20.0}}},
        {"match_phrase_prefix": {"itemName": {"query": kw, "boost": 5.0}}},
        {"match": {"itemName.autocomplete": {"query": kw, "boost": 2.0}}},
        {"match": {"itemName": {"query": kw, "fuzziness": "AUTO", "boost": 2.0}}},
        {"match": {"itemName.ngram": {"query": kw, "minimum_should_match": "50%", "boost": 1.0}}},
        {"match_phrase_prefix": {"itemNameChosung": {"query": kw, "boost": 3.0}}},
    ]}}


def evaluate(index, qfn, docs):
    recalls, rrs = [], []
    for kw, needle, _ in TESTSET:
        hits = req("POST", f"/{index}/_search",
                   {"size": 10, "_source": ["itemName"], "query": qfn(kw)})["hits"]["hits"]
        names = [h["_source"]["itemName"] for h in hits]
        rel = [needle in n for n in names]
        total_rel = sum(1 for d in docs if needle in d["itemName"])
        recalls.append(sum(rel) / min(total_rel, 10) if total_rel else 0.0)
        rrs.append(next((1.0 / (i + 1) for i, ok in enumerate(rel) if ok), 0.0))
    return recalls, rrs


def latency(index, qfn, kw, n=50):
    ts = []
    for _ in range(n):
        s = time.perf_counter()
        req("POST", f"/{index}/_search", {"size": 20, "query": qfn(kw)})
        ts.append((time.perf_counter() - s) * 1000)
    ts.sort()
    return statistics.median(ts), ts[int(n * 0.95)]


def main():
    docs = []
    r = req("POST", "/drug_info/_search?scroll=2m", {"size": 1000, "query": {"match_all": {}}})
    sid = r["_scroll_id"]
    while r["hits"]["hits"]:
        docs += [h["_source"] for h in r["hits"]["hits"]]
        r = req("POST", "/_search/scroll", {"scroll": "2m", "scroll_id": sid})
    print(f"수집: {len(docs)}건")

    settings = json.load(open(SETTINGS_PATH))
    for name, s, m in [("drug_bench_old", None, OLD_MAPPING), ("drug_bench_new", settings, NEW_MAPPING)]:
        try:
            req("DELETE", "/" + name)
        except urllib.error.HTTPError:
            pass
        body = {"mappings": m}
        if s:
            body["settings"] = s
        req("PUT", "/" + name, body)
        bulk_load(name, docs)

    or_, orr = evaluate("drug_bench_old", old_query, docs)
    nr, nrr = evaluate("drug_bench_new", new_query, docs)

    print("\n%-16s %-13s | %-9s %-9s | %-8s %-8s"
          % ("query", "유형", "old R@10", "new R@10", "old RR", "new RR"))
    print("-" * 78)
    for (kw, _, typ), a, b, c, d in zip(TESTSET, or_, nr, orr, nrr):
        print("%-16s %-13s | %-9.2f %-9.2f | %-8.2f %-8.2f" % (kw, typ, a, b, c, d))
    print("-" * 78)
    print("%-16s %-13s | %-9.3f %-9.3f | %-8.3f %-8.3f"
          % ("평균", "", statistics.mean(or_), statistics.mean(nr),
             statistics.mean(orr), statistics.mean(nrr)))

    print("\n--- latency (동일 쿼리 50회, size=20) ---")
    for kw in ["타이레", "게보린", "아세트아미노펜"]:
        op50, op95 = latency("drug_bench_old", old_query, kw)
        np50, np95 = latency("drug_bench_new", new_query, kw)
        print(f"  '{kw}':  old p50={op50:.1f}ms p95={op95:.1f}ms  |  new p50={np50:.1f}ms p95={np95:.1f}ms")

    for name in ["drug_bench_old", "drug_bench_new"]:
        req("DELETE", "/" + name)
    print("\n벤치 인덱스 정리 완료")


if __name__ == "__main__":
    main()
