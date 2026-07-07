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
        "\\*([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|캅셀|시럽|액|연고|크림|주사|산|패치))"
    );
    private static final Pattern NON_STARRED_DRUG_NAME_PATTERN = Pattern.compile(
        "^([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|캅셀|시럽|액|연고|크림|주사|산|패치))(?:\\(|_|\\d)",
        Pattern.MULTILINE
    );
    private static final Pattern DRUG_NAME_PATTERN = Pattern.compile(
        "([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|캅셀|시럽|액|연고|크림|주사|산|패치))"
    );
    private static final Pattern TIMES_PER_DAY_PATTERN = Pattern.compile(
        "1일\\s*투여\\s*횟수\\s*(\\d+)|1일\\s*복용\\s*횟수\\s*(\\d+)|1일\\s*(\\d+)\\s*회|하루\\s*(\\d+)\\s*(?:번|회)|씩\\s*(\\d+)\\s*회"
    );
    // ⚡ 수정: mg/ml/g 뿐 아니라 한글 표기 단위(밀리그람, 그람, 밀리리터)도 인식
    private static final Pattern DOSAGE_PER_TIME_PATTERN = Pattern.compile(
        "1회\\s*투약량\\s*(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|캅셀|ml|mg|g|포|밀리그람|그람|밀리리터)?|" +
            "1회\\s*(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|캅셀|ml|mg|g|포|밀리그람|그람|밀리리터)|" +
            "한\\s*번에\\s*(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|캅셀)|" +
            "(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|캅셀|ml|mg|g|포|밀리그람|그람|밀리리터)\\s*씩"
    );
    // ⚡ 신규: "N정씩N회N일분" 압축 패턴 (dosage+unit+times+days 한번에)
    private static final Pattern COMPACT_DOSAGE_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s*(정|캡슐|캅셀|ml|mg|g|포|밀리그람|그람|밀리리터)\\s*씩\\s*(\\d+)\\s*회\\s*(\\d+)\\s*일분"
    );
    private static final Pattern INLINE_NUMBERS_PATTERN = Pattern.compile(
        "(\\d+(?:\\.\\d+)?)\\s+(\\d+)\\s+(\\d+)"
    );
    // ⚡ 신규: 이름들이 연속 나열되고 뒤에 숫자만 줄줄이 나오는 표 형태 (위더스 약봉투 등)
    private static final Pattern STANDALONE_NUMBER_LINE = Pattern.compile(
        "^\\s*(\\d+(?:\\.\\d+)?)\\s*$", Pattern.MULTILINE
    );
    private static final Pattern TOTAL_DAYS_PATTERN = Pattern.compile(
        "총\\s*투약\\s*일수\\s*(\\d+)|총\\s*복용\\s*일수\\s*(\\d+)|(\\d+)\\s*일(?:분|치|간)"
    );

    // ⚡ 수정: "계산"/"계산서" 같은 우연히 '산' 접미사를 가진 비약품 토큰 추가
    private static final Set<String> DRUG_NAME_BLACKLIST = Set.of(
        "약제비총액", "본인부담금", "보험자부담금", "총수납금액", "현금영수증", "비급여및전액본인부담금",
        "계산", "계산서"
    );
    private static final Pattern LINE_DRUG_NAME_PATTERN = Pattern.compile(
        "^([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|캅셀|시럽|액|연고|크림|주사|산|패치))(?:\\(|_|\\d|\\s*$)",
        Pattern.MULTILINE
    );
    private static final Pattern LABEL_DOSAGE_PATTERN = Pattern.compile(
        "1회\\s*투약량\\s*\\d|1일\\s*투여\\s*횟수\\s*\\d|총\\s*투약\\s*일수\\s*\\d"
    );

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

    private boolean isPharmacyReceipt(String rawText) {
        return STARRED_DRUG_NAME_PATTERN.matcher(rawText).find();
    }

    private List<ParsedOcrData> parseByPharmacyReceipt(String rawText) {
        List<String> names = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();

        Matcher starredMatcher = STARRED_DRUG_NAME_PATTERN.matcher(rawText);
        while (starredMatcher.find()) {
            String name = starredMatcher.group(1);
            if (DRUG_NAME_BLACKLIST.contains(name)) continue;
            names.add(name);
            starts.add(starredMatcher.start());
        }

        if (names.isEmpty()) return List.of(parseByRegex(rawText));

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

        List<ParsedOcrData> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int blockStart = starts.get(i);
            int blockEnd = (i + 1 < starts.size()) ? starts.get(i + 1) : rawText.length();
            String block = rawText.substring(blockStart, blockEnd);

            // ⚡ 신규: 압축 패턴("1정씩2회7일분") 우선 시도
            ParsedOcrData compact = tryCompactDosage(names.get(i), block);
            String dosage;
            Integer times;
            Integer days;

            if (compact != null) {
                dosage = compact.dosagePerTime();
                times = compact.timesPerDay();
                days = compact.totalDays();
            } else {
                dosage = extractDosagePerTime(block);
                times = extractTimesPerDay(block);
                days = extractTotalDays(block);

                // ⚡ 신규: OCR 순서가 뒤섞여 현재 블록에 값이 전혀 없으면
                // 다음 약 블록까지 확장해서 재탐색 (뮤테란캅셀 케이스)
                if (dosage == null && times == null && days == null) {
                    int extendedEnd = (i + 2 < starts.size()) ? starts.get(i + 2) : rawText.length();
                    String extended = rawText.substring(blockStart, extendedEnd);
                    ParsedOcrData compactExt = tryCompactDosage(names.get(i), extended);
                    if (compactExt != null) {
                        dosage = compactExt.dosagePerTime();
                        times = compactExt.timesPerDay();
                        days = compactExt.totalDays();
                    } else {
                        dosage = extractDosagePerTime(extended);
                        times = extractTimesPerDay(extended);
                        days = extractTotalDays(extended);
                    }
                }

                if (dosage != null && dosage.matches("\\d+(?:\\.\\d+)?")) {
                    String unit = inferDosageUnit(names.get(i));
                    if (unit != null) dosage = dosage + unit;
                }

                if (dosage == null && times == null && days == null) {
                    Matcher inlineMatcher = INLINE_NUMBERS_PATTERN.matcher(block);
                    if (inlineMatcher.find()) {
                        String rawDosage = inlineMatcher.group(1);
                        String unit = inferDosageUnit(names.get(i));
                        dosage = rawDosage.matches("\\d+(?:\\.\\d+)?") && unit != null ? rawDosage + unit : rawDosage;
                        times = Integer.parseInt(inlineMatcher.group(2));
                        days = Integer.parseInt(inlineMatcher.group(3));
                    }
                }
            }

            result.add(new ParsedOcrData(names.get(i), dosage, times, days));
        }

        // ⚡ 신규: 전부 못 찾았으면 "이름들 나열 후 숫자만 그룹으로" 형태 시도 (위더스 약봉투)
        if (result.stream().allMatch(d -> d.dosagePerTime() == null && d.timesPerDay() == null && d.totalDays() == null)) {
            List<ParsedOcrData> grouped = parseGroupedTrailingNumbers(rawText, names, starts);
            if (grouped != null) return grouped;
        }

        return result;
    }

    // ⚡ 신규: *약이름 *약이름 *약이름 \n 1 3 3 1 3 3 1 3 3 형태 파싱
    private List<ParsedOcrData> parseGroupedTrailingNumbers(String rawText, List<String> names, List<Integer> starts) {
        int lastNameStart = starts.get(starts.size() - 1);
        String tail = rawText.substring(lastNameStart);

        List<String> numbers = new ArrayList<>();
        Matcher m = STANDALONE_NUMBER_LINE.matcher(tail);
        while (m.find()) numbers.add(m.group(1));

        int needed = names.size() * 3;
        if (numbers.size() < needed) return null;

        List<ParsedOcrData> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String dosageRaw = numbers.get(i * 3);
            Integer times = parseIntOrNull(numbers.get(i * 3 + 1));
            Integer days = parseIntOrNull(numbers.get(i * 3 + 2));
            String unit = inferDosageUnit(names.get(i));
            String dosage = (unit != null && dosageRaw.matches("\\d+(?:\\.\\d+)?")) ? dosageRaw + unit : dosageRaw;
            result.add(new ParsedOcrData(names.get(i), dosage, times, days));
        }
        return result;
    }

    // ⚡ 신규: "1정씩2회7일분" 압축 패턴 → dosage/times/days 한번에 추출
    private ParsedOcrData tryCompactDosage(String drugName, String block) {
        Matcher m = COMPACT_DOSAGE_PATTERN.matcher(block);
        if (m.find()) {
            String dosage = m.group(1) + m.group(2);
            int times = Integer.parseInt(m.group(3));
            int days = Integer.parseInt(m.group(4));
            return new ParsedOcrData(drugName, dosage, times, days);
        }
        return null;
    }

    private String inferDosageUnit(String drugName) {
        if (drugName.endsWith("정")) return "정";
        if (drugName.endsWith("캡슐")) return "캡슐";
        if (drugName.endsWith("캅셀")) return "캡슐";
        if (drugName.endsWith("시럽")) return "ml";
        if (drugName.endsWith("액")) return "ml";
        return null;
    }

    // ── 텍스트 순서형 다중 약 감지 + 파싱 ────────────────────────────────────

    private boolean isTextSequentialMulti(String rawText) {
        Matcher m = LABEL_DOSAGE_PATTERN.matcher(rawText);
        int count = 0;
        while (m.find()) {
            if (++count >= 2) return true;
        }
        return false;
    }

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

            // ⚡ 신규: 압축 패턴 우선 시도 (액시티딘캅셀\n1캡슐씩2회7일분 케이스)
            ParsedOcrData compact = tryCompactDosage(names.get(i), block);
            if (compact != null) {
                result.add(compact);
                continue;
            }

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
        if (LABEL_DOSAGE_PATTERN.matcher(rawText).find()) return false;
        int matchCount = 0;
        if (rawText.contains("명칭") || rawText.contains("약품명")) matchCount++;
        if (rawText.contains("투약량") || rawText.contains("복용량")) matchCount++;
        if (rawText.contains("투여횟수") || rawText.contains("복용횟수") || rawText.contains("횟수")) matchCount++;
        if (rawText.contains("투약일수") || rawText.contains("복용일수") || rawText.contains("일수")) matchCount++;
        return matchCount >= 3;
    }

    // ── boundingPoly 좌표 기반 파싱 ───────────────────────────────────────────

    private List<ParsedOcrData> parseByCoordinates(List<NaverOcrApiResponse.Field> fields) {
        List<FieldWithCenter> positioned = fields.stream()
            .filter(f -> f.boundingPoly() != null && !f.boundingPoly().vertices().isEmpty())
            .map(f -> new FieldWithCenter(f, centerX(f), centerY(f)))
            .sorted(Comparator.comparingDouble(FieldWithCenter::y))
            .toList();

        List<List<FieldWithCenter>> rows = clusterRows(positioned);
        rows.forEach(row -> row.sort(Comparator.comparingDouble(FieldWithCenter::x)));

        ColumnIndex colIdx = detectColumnIndex(rows);
        if (colIdx.drugNameCol() < 0) {
            return List.of(parseByRegex(buildRawText(fields)));
        }

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
            String dosage = dosageRaw != null
                ? (dosageRaw.matches("\\d+(?:\\.\\d+)?") && unit != null ? dosageRaw + unit : dosageRaw)
                : null;
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

    // ⚡ 수정: 좌표 기반 파싱에도 블랙리스트 적용 (첫 매치가 블랙리스트면 다음 매치 탐색)
    private String refineDrugName(String raw) {
        String cleaned = raw.replaceAll("\\[.*?\\]", "").trim();
        Matcher m = DRUG_NAME_PATTERN.matcher(cleaned);
        while (m.find()) {
            String name = m.group(1);
            if (!DRUG_NAME_BLACKLIST.contains(name)) return name;
        }
        return null;
    }

    // ── 정규식 기반 단일 약 파싱 ─────────────────────────────────────────────

    private ParsedOcrData parseByRegex(String rawText) {
        String name = extractDrugName(rawText);

        // ⚡ 신규: 압축 패턴 우선 시도
        if (name != null) {
            ParsedOcrData compact = tryCompactDosage(name, rawText);
            if (compact != null) return compact;
        }

        return new ParsedOcrData(
            name,
            extractDosagePerTime(rawText),
            extractTimesPerDay(rawText),
            extractTotalDays(rawText)
        );
    }

    // ⚡ 수정: 블랙리스트 매치는 건너뛰고 다음 후보 탐색
    private String extractDrugName(String text) {
        Matcher starred = STARRED_DRUG_NAME_PATTERN.matcher(text);
        while (starred.find()) {
            String name = starred.group(1);
            if (!DRUG_NAME_BLACKLIST.contains(name)) return name;
        }
        Matcher m = DRUG_NAME_PATTERN.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            if (!DRUG_NAME_BLACKLIST.contains(name)) return name;
        }
        return null;
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

    private record FieldWithCenter(NaverOcrApiResponse.Field field, double x, double y) {}

    private record ColumnIndex(int headerRowIdx, int drugNameCol, int dosageCol, int timesCol, int daysCol) {}
}