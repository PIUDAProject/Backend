package com.piuda.callcare.domain.ocrresult.service;

import com.piuda.callcare.domain.ocrresult.dto.OcrParseResult;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class OcrParser {

    private static final double ROW_CLUSTER_THRESHOLD = 15.0;

    private static final Pattern STARRED_DRUG_NAME_PATTERN = Pattern.compile(
        "\\*([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|시럽|액|연고|크림|주사|산|패치))"
    );
    // 줄 첫 글자로 시작하고 '(' 또는 '_'가 뒤따르는 비별표 약 이름 (에페신정(성분명) 형태)
    private static final Pattern NON_STARRED_DRUG_NAME_PATTERN = Pattern.compile(
        "^([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|시럽|액|연고|크림|주사|산|패치))(?:\\(|_)",
        Pattern.MULTILINE
    );
    private static final Pattern DRUG_NAME_PATTERN = Pattern.compile(
        "([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|시럽|액|연고|크림|주사|산|패치))"
    );
    private static final Pattern TIMES_PER_DAY_PATTERN = Pattern.compile(
        "1일\\s*투여\\s*횟수\\s*(\\d+)|1일\\s*복용\\s*횟수\\s*(\\d+)|1일\\s*(\\d+)\\s*회|하루\\s*(\\d+)\\s*(?:번|회)|씩\\s*(\\d+)\\s*회"
    );
    private static final Pattern DOSAGE_PER_TIME_PATTERN = Pattern.compile(
        "1회\\s*투약량\\s*(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|ml|mg|g|포)?|" +
        "1회\\s*(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|ml|mg|g|포)|" +
        "한\\s*번에\\s*(\\d+(?:\\.\\d+)?)\\s*(정|캡슐)|" +
        "(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|ml|mg|g|포)\\s*씩"
    );
    private static final Pattern TOTAL_DAYS_PATTERN = Pattern.compile(
        "총\\s*투약\\s*일수\\s*(\\d+)|총\\s*복용\\s*일수\\s*(\\d+)|(\\d+)\\s*일(?:분|치|간)"
    );

    private static final Set<String> DRUG_NAME_BLACKLIST = Set.of(
        "약제비총액", "본인부담금", "보험자부담금", "총수납금액", "현금영수증", "비급여및전액본인부담금"
    );
    // 줄 첫 글자 약 이름 — 단독, (성분명), _ 접미사 모두 허용
    private static final Pattern LINE_DRUG_NAME_PATTERN = Pattern.compile(
        "^([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|시럽|액|연고|크림|주사|산|패치))(?:\\(|_|\\s*$)",
        Pattern.MULTILINE
    );
    // "1회투약량1" 처럼 숫자가 바로 붙은 라벨형 패턴 → 텍스트 순서형 약봉투 판별용
    private static final Pattern LABEL_DOSAGE_PATTERN = Pattern.compile(
        "1회\\s*투약량\\s*\\d|1일\\s*투여\\s*횟수\\s*\\d|총\\s*투약\\s*일수\\s*\\d"
    );

    // 표 처방전 컬럼 헤더 키워드
    private static final List<String> DRUG_NAME_KEYWORDS = List.of("명칭", "약품명", "의약품");
    private static final List<String> DOSAGE_KEYWORDS   = List.of("투약량", "복용량", "1회");
    private static final List<String> TIMES_KEYWORDS    = List.of("투여횟수", "복용횟수", "횟수");
    private static final List<String> DAYS_KEYWORDS     = List.of("투약일수", "복용일수", "일수");

    public OcrParseResult parse(List<NaverOcrApiResponse.Field> fields, OcrType ocrType) {
        String rawText = buildRawText(fields);

        boolean hasCoordinates = fields.stream()
                .anyMatch(f -> f.boundingPoly() != null && !f.boundingPoly().vertices().isEmpty());

        List<ParsedOcrData> parsedDrugs;
        if (isPharmacyReceipt(rawText)) {
            parsedDrugs = parseByPharmacyReceipt(rawText);
        } else if (hasCoordinates && isTablePrescription(rawText)) {
            parsedDrugs = parseByCoordinates(fields);
        } else if (isTextSequentialMulti(rawText)) {
            parsedDrugs = parseByTextSequential(rawText);
        } else {
            parsedDrugs = List.of(parseByRegex(rawText));
        }

        return new OcrParseResult(rawText, parsedDrugs);
    }

    // ── rawText 조립 ──────────────────────────────────────────────────────────

    private String buildRawText(List<NaverOcrApiResponse.Field> fields) {
        return fields.stream()
                .map(f -> f.inferText() + (Boolean.TRUE.equals(f.lineBreak()) ? "\n" : " "))
                .collect(Collectors.joining())
                .trim();
    }

    // ── 약봉투/영수증 형식 감지 + 파싱 ──────────────────────────────────────

    // *약이름 패턴이 2개 이상이면 약봉투/영수증 형식으로 판단
    private boolean isPharmacyReceipt(String rawText) {
        Matcher m = STARRED_DRUG_NAME_PATTERN.matcher(rawText);
        int count = 0;
        while (m.find()) {
            if (++count >= 2) return true;
        }
        return false;
    }

    // *약이름 + 줄 첫 약이름 통합 → 위치 순 정렬 후 블록 분리 → 각 블록에서 숫자 필드 추출
    private List<ParsedOcrData> parseByPharmacyReceipt(String rawText) {
        List<String> names = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();

        // Step 1: *약이름 수집
        Matcher starredMatcher = STARRED_DRUG_NAME_PATTERN.matcher(rawText);
        while (starredMatcher.find()) {
            names.add(starredMatcher.group(1));
            starts.add(starredMatcher.start());
        }

        if (names.isEmpty()) return List.of(parseByRegex(rawText));

        // Step 2: 줄 첫 글자 약이름 수집 (에페신정(성분명) 형태, 이미 찾은 것 제외)
        Set<String> foundNames = new HashSet<>(names);
        Matcher nonStarredMatcher = NON_STARRED_DRUG_NAME_PATTERN.matcher(rawText);
        while (nonStarredMatcher.find()) {
            String name = nonStarredMatcher.group(1);
            if (foundNames.contains(name) || DRUG_NAME_BLACKLIST.contains(name)) continue;
            int pos = nonStarredMatcher.start(1);
            int insertIdx = 0;
            while (insertIdx < starts.size() && starts.get(insertIdx) < pos) insertIdx++;
            names.add(insertIdx, name);
            starts.add(insertIdx, pos);
            foundNames.add(name);
        }

        // Step 3: 블록별 데이터 추출
        List<ParsedOcrData> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int blockStart = starts.get(i);
            int blockEnd = (i + 1 < starts.size()) ? starts.get(i + 1) : rawText.length();
            String block = rawText.substring(blockStart, blockEnd);

            String dosage = extractDosagePerTime(block);
            // 단위 없이 숫자만 나온 경우 약 이름에서 단위 추론
            if (dosage != null && dosage.matches("\\d+(?:\\.\\d+)?")) {
                String unit = inferDosageUnit(names.get(i));
                if (unit != null) dosage = dosage + unit;
            }

            result.add(new ParsedOcrData(
                    names.get(i),
                    dosage,
                    extractTimesPerDay(block),
                    extractTotalDays(block)
            ));
        }

        return result;
    }

    // 약 이름 접미사로 복용 단위 추론
    private String inferDosageUnit(String drugName) {
        if (drugName.endsWith("정")) return "정";
        if (drugName.endsWith("캡슐")) return "캡슐";
        if (drugName.endsWith("시럽")) return "ml";
        if (drugName.endsWith("액")) return "ml";
        return null;
    }

    // ── 텍스트 순서형 다중 약 감지 + 파싱 (패턴 3: *없는 약이름\n1회투약량N 반복) ──

    // 라벨형 패턴이 2회 이상 → 여러 약이 텍스트 순서로 나열된 형식
    private boolean isTextSequentialMulti(String rawText) {
        Matcher m = LABEL_DOSAGE_PATTERN.matcher(rawText);
        int count = 0;
        while (m.find()) {
            if (++count >= 2) return true;
        }
        return false;
    }

    // 줄 첫 약이름 기준으로 블록 분리 → 각 블록에서 숫자 필드 추출
    private List<ParsedOcrData> parseByTextSequential(String rawText) {
        List<String> names = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();

        Matcher m = LINE_DRUG_NAME_PATTERN.matcher(rawText);
        while (m.find()) {
            String name = m.group(1);
            if (DRUG_NAME_BLACKLIST.contains(name)) continue;
            names.add(name);
            starts.add(m.start(1));
        }

        if (names.isEmpty()) return List.of(parseByRegex(rawText));

        List<ParsedOcrData> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int blockStart = starts.get(i);
            int blockEnd = (i + 1 < starts.size()) ? starts.get(i + 1) : rawText.length();
            String block = rawText.substring(blockStart, blockEnd);

            String dosage = extractDosagePerTime(block);
            if (dosage != null && dosage.matches("\\d+(?:\\.\\d+)?")) {
                String unit = inferDosageUnit(names.get(i));
                if (unit != null) dosage = dosage + unit;
            }

            result.add(new ParsedOcrData(names.get(i), dosage, extractTimesPerDay(block), extractTotalDays(block)));
        }

        return result.isEmpty() ? List.of(parseByRegex(rawText)) : result;
    }

    // ── 표 처방전 감지 ────────────────────────────────────────────────────────

    private boolean isTablePrescription(String rawText) {
        // 숫자가 바로 붙은 라벨형 패턴이 있으면 텍스트 순서형 → 표 아님
        if (LABEL_DOSAGE_PATTERN.matcher(rawText).find()) return false;
        // 컬럼 헤더 3개 이상이면 표 처방전으로 판단
        int matchCount = 0;
        if (rawText.contains("명칭") || rawText.contains("약품명")) matchCount++;
        if (rawText.contains("투약량") || rawText.contains("복용량")) matchCount++;
        if (rawText.contains("투여횟수") || rawText.contains("복용횟수") || rawText.contains("횟수")) matchCount++;
        if (rawText.contains("투약일수") || rawText.contains("복용일수") || rawText.contains("일수")) matchCount++;
        return matchCount >= 3;
    }

    // ── boundingPoly 좌표 기반 파싱 ───────────────────────────────────────────

    private List<ParsedOcrData> parseByCoordinates(List<NaverOcrApiResponse.Field> fields) {
        // 좌표가 있는 field만 추출하고 y 중심값 계산
        List<FieldWithCenter> positioned = fields.stream()
                .filter(f -> f.boundingPoly() != null && !f.boundingPoly().vertices().isEmpty())
                .map(f -> new FieldWithCenter(f, centerX(f), centerY(f)))
                .sorted(Comparator.comparingDouble(FieldWithCenter::y))
                .toList();

        // y 좌표 기준으로 행 클러스터링
        List<List<FieldWithCenter>> rows = clusterRows(positioned);

        // 각 행 안에서 x 좌표 오름차순 정렬
        rows.forEach(row -> row.sort(Comparator.comparingDouble(FieldWithCenter::x)));

        // 헤더 행에서 컬럼 인덱스 파악
        ColumnIndex colIdx = detectColumnIndex(rows);
        if (colIdx.drugNameCol() < 0) {
            // 헤더를 찾지 못하면 정규식 폴백
            return List.of(parseByRegex(buildRawText(fields)));
        }

        // 헤더 행 이후 데이터 행에서 약 추출
        List<ParsedOcrData> result = new ArrayList<>();
        for (int i = colIdx.headerRowIdx() + 1; i < rows.size(); i++) {
            List<String> texts = rows.get(i).stream()
                    .map(fw -> fw.field().inferText())
                    .toList();

            String drugName = getCol(texts, colIdx.drugNameCol());
            if (drugName == null) continue;
            drugName = refineDrugName(drugName);
            if (drugName == null) continue;

            String dosageRaw = getCol(texts, colIdx.dosageCol());
            String unit = inferDosageUnit(drugName);
            String dosage = dosageRaw != null ? dosageRaw + (unit != null ? unit : "") : null;
            Integer times = parseIntOrNull(getCol(texts, colIdx.timesCol()));
            Integer days  = parseIntOrNull(getCol(texts, colIdx.daysCol()));

            result.add(new ParsedOcrData(drugName, dosage, times, days));
        }

        return result.isEmpty() ? List.of(parseByRegex(buildRawText(fields))) : result;
    }

    private List<List<FieldWithCenter>> clusterRows(List<FieldWithCenter> sorted) {
        List<List<FieldWithCenter>> rows = new ArrayList<>();
        for (FieldWithCenter fw : sorted) {
            if (rows.isEmpty() || fw.y() - lastY(rows) > ROW_CLUSTER_THRESHOLD) {
                rows.add(new ArrayList<>(List.of(fw)));
            } else {
                rows.get(rows.size() - 1).add(fw);
            }
        }
        return rows;
    }

    private ColumnIndex detectColumnIndex(List<List<FieldWithCenter>> rows) {
        for (int i = 0; i < rows.size(); i++) {
            List<String> texts = rows.get(i).stream()
                    .map(fw -> fw.field().inferText())
                    .toList();
            String rowText = String.join("", texts);

            boolean isHeader = DRUG_NAME_KEYWORDS.stream().anyMatch(rowText::contains)
                    || DOSAGE_KEYWORDS.stream().anyMatch(rowText::contains);

            if (isHeader) {
                int drugCol = -1, dosageCol = -1, timesCol = -1, daysCol = -1;
                for (int j = 0; j < texts.size(); j++) {
                    String t = texts.get(j);
                    if (DRUG_NAME_KEYWORDS.stream().anyMatch(t::contains)) drugCol  = j;
                    else if (DOSAGE_KEYWORDS.stream().anyMatch(t::contains)) dosageCol = j;
                    else if (TIMES_KEYWORDS.stream().anyMatch(t::contains)) timesCol  = j;
                    else if (DAYS_KEYWORDS.stream().anyMatch(t::contains)) daysCol   = j;
                }
                return new ColumnIndex(i, drugCol, dosageCol, timesCol, daysCol);
            }
        }
        return new ColumnIndex(-1, -1, -1, -1, -1);
    }

    // 약 이름에서 급여코드([급여][...]) 같은 접두어 제거
    private String refineDrugName(String raw) {
        String cleaned = raw.replaceAll("\\[.*?\\]", "").trim();
        Matcher m = DRUG_NAME_PATTERN.matcher(cleaned);
        return m.find() ? m.group(1) : null;
    }

    // ── 정규식 기반 단일 약 파싱 ─────────────────────────────────────────────

    private ParsedOcrData parseByRegex(String rawText) {
        return new ParsedOcrData(
                extractDrugName(rawText),
                extractDosagePerTime(rawText),
                extractTimesPerDay(rawText),
                extractTotalDays(rawText)
        );
    }

    private String extractDrugName(String text) {
        Matcher starred = STARRED_DRUG_NAME_PATTERN.matcher(text);
        if (starred.find()) return starred.group(1);
        Matcher m = DRUG_NAME_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private Integer extractTimesPerDay(String text) {
        Matcher m = TIMES_PER_DAY_PATTERN.matcher(text);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) return Integer.parseInt(m.group(i));
            }
        }
        return null;
    }

    private String extractDosagePerTime(String text) {
        Matcher m = DOSAGE_PER_TIME_PATTERN.matcher(text);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i += 2) {
                String amount = m.group(i);
                if (amount != null) {
                    String unit = (i + 1 <= m.groupCount()) ? m.group(i + 1) : null;
                    return unit != null ? amount + unit : amount;
                }
            }
        }
        return null;
    }

    private Integer extractTotalDays(String text) {
        Matcher m = TOTAL_DAYS_PATTERN.matcher(text);
        if (m.find()) {
            for (int i = 1; i <= m.groupCount(); i++) {
                if (m.group(i) != null) return Integer.parseInt(m.group(i));
            }
        }
        return null;
    }

    // ── 좌표 유틸 ─────────────────────────────────────────────────────────────

    private double centerX(NaverOcrApiResponse.Field field) {
        return field.boundingPoly().vertices().stream()
                .mapToDouble(NaverOcrApiResponse.Vertex::x).average().orElse(0);
    }

    private double centerY(NaverOcrApiResponse.Field field) {
        return field.boundingPoly().vertices().stream()
                .mapToDouble(NaverOcrApiResponse.Vertex::y).average().orElse(0);
    }

    private double lastY(List<List<FieldWithCenter>> rows) {
        List<FieldWithCenter> last = rows.get(rows.size() - 1);
        return last.get(last.size() - 1).y();
    }

    private String getCol(List<String> texts, int col) {
        return (col >= 0 && col < texts.size()) ? texts.get(col).trim() : null;
    }

    private Integer parseIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    // ── 내부 레코드 ───────────────────────────────────────────────────────────

    private record FieldWithCenter(NaverOcrApiResponse.Field field, double x, double y) {}

    private record ColumnIndex(int headerRowIdx, int drugNameCol, int dosageCol, int timesCol, int daysCol) {}
}
