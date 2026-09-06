package com.piuda.callcare.domain.ocrresult.service;

import com.piuda.callcare.domain.ocrresult.dto.OcrParseResult;
import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.dto.response.NaverOcrApiResponse;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
        "^([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|캅셀|시럽|액|연고|크림|주사|산|패치))(?:\\(|\\[|_|\\d)",
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

    // ⚡ 수정: 비약품 토큰 + 제형만 나타내는 단어(약봉투 설명줄 "코팅정" 등)를 약 이름으로 오인하지 않도록
    private static final Set<String> DRUG_NAME_BLACKLIST = Set.of(
        "약제비총액", "본인부담금", "보험자부담금", "총수납금액", "현금영수증", "비급여및전액본인부담금",
        "계산", "계산서",
        "코팅정", "필름코팅정", "서방정", "장용정", "당의정", "설하정", "나정",
        "경질캡슐", "연질캡슐", "경질캅셀"
    );
    private static final Pattern LINE_DRUG_NAME_PATTERN = Pattern.compile(
        "^([가-힣a-zA-Z][가-힣a-zA-Z0-9]*(?:정|캡슐|캅셀|시럽|액|연고|크림|주사|산|패치))(?:\\(|\\[|_|\\d|\\s*$)",
        Pattern.MULTILINE
    );
    // ⚡ 수정: 기존 \s* 는 줄바꿈·공백을 삼켜 표 헤더("1회 투약량" + 다음 칸 "1일...")를
    // 라벨로 오인함. 약봉투 라벨은 키워드가 붙어("1회투약량1") 나오고 표 헤더는 띄어써서
    // ("1회 투약량") 나뉘므로, 키워드는 글자를 붙이고 값 숫자 앞에만 공백/탭을 허용한다.
    private static final Pattern LABEL_DOSAGE_PATTERN = Pattern.compile(
        "1회투약량[ \\t]*\\d|1일투여횟수[ \\t]*\\d|총투약일수[ \\t]*\\d"
    );

    // 처방일 우선, 없으면 조제일 fallback
    private static final Pattern PRESCRIPTION_DATE_PATTERN = Pattern.compile(
        "(?:처방일|처방일자|처방전발행일)\\s*[:\\-]?\\s*(\\d{4})\\s*[.년\\-]\\s*(\\d{1,2})\\s*[.월\\-]?\\s*(\\d{1,2})"
    );
    private static final Pattern DISPENSE_DATE_PATTERN = Pattern.compile(
        "(?:조제일|조제일자)\\s*[:\\-]?\\s*(\\d{4})\\s*[.년\\-]\\s*(\\d{1,2})\\s*[.월\\-]?\\s*(\\d{1,2})"
    );

    // 병원 처방전 약 줄: "보험코드(8~10자리) + 제품명 [+ (내복)]"
    private static final Pattern PRESCRIPTION_CODE_LINE = Pattern.compile("\\d{8,10}\\s+[가-힣A-Za-z]");
    private static final Pattern DRUG_CODE = Pattern.compile("^\\d{8,10}$");
    private static final Pattern SINGLE_NUMBER = Pattern.compile("^[0-9lITｌ]$");

    private static final List<String> DRUG_NAME_KEYWORDS = List.of("명칭", "약품명", "의약품");
    private static final List<String> DOSAGE_KEYWORDS   = List.of("투약량", "복용량", "1회");
    private static final List<String> TIMES_KEYWORDS    = List.of("투여횟수", "복용횟수", "횟수");
    private static final List<String> DAYS_KEYWORDS     = List.of("투약일수", "복용일수", "일수");

    /**
     * 병원 처방전(약국에서 주는 약봉투·영수증이 아닌)이면 true.
     * <p>
     * 처방전은 표 서식이라 저화질이어도 좌표 알고리즘(파서)이 숫자 컬럼을 정확히 잡는 반면
     * LLM은 좌표 텍스트로 표를 못 읽는다(실측). 하이브리드 라우팅에서 "파서로 보낼 것" 신호.
     * <p>
     * 서식별 고유 문구로 구분한다:
     * <ul>
     *   <li>처방전: "처방전" 제목 / "처방 의약품" / "교부번호"·"교부일" / 보험코드 줄</li>
     *   <li>약봉투: "복약안내" / {@code *약이름} 별표 / "N정씩N회N일분"</li>
     *   <li>영수증: "약제비" / "계산서" / "본인부담금"</li>
     * </ul>
     */
    public boolean isPrescription(List<NaverOcrApiResponse.Field> fields) {
        String t = buildRawText(fields);

        boolean prescriptionSignal = hasPrescriptionCodeLines(t)
            || t.contains("처방전")
            || t.contains("처방 의약품") || t.contains("처방의약품")
            || t.contains("교부번호") || t.contains("교부일");

        boolean drugBagSignal = t.contains("복약안내") || t.contains("약봉투")
            || isPharmacyReceipt(t) || isTextSequentialMulti(t);

        boolean receiptSignal = t.contains("약제비") || t.contains("계산서") || t.contains("본인부담금");

        return prescriptionSignal && !drugBagSignal && !receiptSignal;
    }

    public OcrParseResult parse(List<NaverOcrApiResponse.Field> fields, OcrType ocrType) {
        String rawText = buildRawText(fields);

        boolean hasCoordinates = fields.stream()
            .anyMatch(f -> f.boundingPoly() != null && !f.boundingPoly().vertices().isEmpty());

        // 순서 주의: 약봉투(별표/라벨/압축형)를 표 처방전보다 먼저 판정한다.
        // 라벨 약봉투도 "투약량/횟수/일수" 키워드를 가져 표로 오인될 수 있기 때문.
        List<ParsedOcrData> parsedDrugs;
        if (isPharmacyReceipt(rawText)) {
            parsedDrugs = parseByPharmacyReceipt(rawText);
        } else if (isTextSequentialMulti(rawText)) {
            parsedDrugs = parseByTextSequential(rawText);
        } else if (hasCoordinates && hasPrescriptionCodeLines(rawText)) {
            // 보험코드 줄이 있는 병원 처방전 — 헤더가 뭉개져도 코드+x좌표로 파싱
            parsedDrugs = parseByPrescriptionCode(fields);
        } else if (hasCoordinates && isTablePrescription(rawText)) {
            parsedDrugs = parseByCoordinates(fields);
        } else {
            parsedDrugs = List.of(parseByRegex(rawText));
        }

        return new OcrParseResult(rawText, parsedDrugs, extractPrescriptionDate(rawText));
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

        // ⚡ 수정: "이름들 나열 후 숫자만 그룹으로" 형태 시도 (위더스 약봉투).
        // 마지막 약 블록이 뒤 숫자를 흡수해 값이 생기면 allMatch가 깨지므로, 절반 이상이 비면 시도한다.
        long emptyCount = result.stream()
            .filter(d -> d.dosagePerTime() == null && d.timesPerDay() == null && d.totalDays() == null)
            .count();
        if (emptyCount * 2 >= result.size()) {
            List<ParsedOcrData> grouped = parseGroupedTrailingNumbers(rawText, names, starts);
            if (grouped != null) return dedupeByName(grouped);
        }

        return dedupeByName(result);
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
        return countMatches(LABEL_DOSAGE_PATTERN, rawText) >= 2
            // ⚡ 신규: 별표·라벨 없이 "이름 줄 + 1정씩1회5일분"이 반복되는 압축형 약봉투
            || countMatches(COMPACT_DOSAGE_PATTERN, rawText) >= 2;
    }

    private int countMatches(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
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

        return result.isEmpty() ? List.of(parseByRegex(rawText)) : dedupeByName(result);
    }

    // ── 보험코드 줄 기반 처방전 파싱 ─────────────────────────────────────────
    // 헤더가 뭉개진 저화질 처방전도, "9자리 코드 + 이름" 줄과 같은 행의 숫자를 x순으로 잡는다.

    private boolean hasPrescriptionCodeLines(String rawText) {
        return countMatches(PRESCRIPTION_CODE_LINE, rawText) >= 2;
    }

    private List<ParsedOcrData> parseByPrescriptionCode(List<NaverOcrApiResponse.Field> fields) {
        List<FieldWithCenter> positioned = fields.stream()
            .filter(f -> f.boundingPoly() != null && !f.boundingPoly().vertices().isEmpty())
            .map(f -> new FieldWithCenter(f, centerX(f), centerY(f)))
            .toList();

        List<FieldWithCenter> codes = positioned.stream()
            .filter(f -> DRUG_CODE.matcher(f.field().inferText().trim()).matches())
            .sorted(Comparator.comparingDouble(FieldWithCenter::y))
            .toList();
        if (codes.size() < 2) {
            return List.of(parseByRegex(buildRawText(fields)));
        }

        // 각 필드를 y가 가장 가까운 코드 행에 배정하되, 행 간격의 절반 이내만 인정한다
        double rowGap = codes.size() > 1 ? (codes.get(codes.size() - 1).y() - codes.get(0).y()) / (codes.size() - 1) : 15;
        double yTol = Math.max(6, rowGap * 0.6);

        List<ParsedOcrData> result = new ArrayList<>();
        for (int r = 0; r < codes.size(); r++) {
            FieldWithCenter code = codes.get(r);
            int idx = r;
            List<FieldWithCenter> row = positioned.stream()
                .filter(f -> Math.abs(f.y() - code.y()) <= yTol && nearestCodeIndex(codes, f.y()) == idx)
                .sorted(Comparator.comparingDouble(FieldWithCenter::x))
                .toList();

            // 이름: 코드 오른쪽의 첫 한글 텍스트 (숫자·식후 등 제외)
            String name = row.stream()
                .filter(f -> f.x() > code.x())
                .map(f -> refineCodeLineName(f.field().inferText()))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
            if (name == null) continue;

            // 숫자: 코드/이름 오른쪽의 한 자리 숫자들을 x순으로 (투약량·횟수·일수 = 앞 3개)
            List<String> nums = row.stream()
                .filter(f -> f.x() > code.x())
                .filter(f -> SINGLE_NUMBER.matcher(f.field().inferText().trim()).matches())
                .map(f -> normalizeDigit(f.field().inferText().trim()))
                .toList();

            String dosage = nums.isEmpty() ? null : nums.get(0);
            String unit = inferDosageUnit(name);
            if (dosage != null && dosage.matches("\\d+") && unit != null) dosage = dosage + unit;
            Integer times = nums.size() > 1 ? parseIntOrNull(nums.get(1)) : null;
            Integer days  = nums.size() > 2 ? parseIntOrNull(nums.get(2)) : null;

            result.add(new ParsedOcrData(name, dosage, times, days));
        }
        return result.isEmpty() ? List.of(parseByRegex(buildRawText(fields))) : dedupeByName(result);
    }

    private static final Pattern USAGE_WORD = Pattern.compile("^(식(후|전|주|간|추)|취침전?|아침|점심|저녁|공복|경구|내복|외용)$");

    // "지스로먹스장250mg(내복)" → "지스로먹스장" (괄호·용량 표기 제거, 용법 단어는 제외)
    private String refineCodeLineName(String raw) {
        String s = raw.replaceAll("\\(.*?\\)", "")
            .replaceAll("\\d+(?:\\.\\d+)?\\s*(?:mg|ml|g|밀리그람|밀리그램|그람|밀리리터|mcg|IU)$", "")
            .trim();
        if (s.length() < 2 || !s.matches(".*[가-힣].*")) return null;
        if (USAGE_WORD.matcher(s).matches()) return null;
        return s;
    }

    private String normalizeDigit(String s) {
        return s.matches("[lITｌ]") ? "1" : s;
    }

    private int nearestCodeIndex(List<FieldWithCenter> codes, double y) {
        int best = 0;
        for (int i = 1; i < codes.size(); i++) {
            if (Math.abs(codes.get(i).y() - y) < Math.abs(codes.get(best).y() - y)) best = i;
        }
        return best;
    }

    // ── 표 처방전 감지 ────────────────────────────────────────────────────────

    private boolean isTablePrescription(String rawText) {
        // 라벨/압축형 약봉투는 parse()에서 isTextSequentialMulti로 먼저 걸러진다.
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

        // ⚡ 수정: 헤더 셀의 순서 인덱스 대신 x중심 좌표를 앵커로 쓴다.
        // 헤더가 "처방 의약품의"+"명칭" 처럼 쪼개지거나 셀이 누락돼도 데이터 셀을 x 최근접 컬럼에 배정한다.
        List<ColumnAnchor> anchors = new ArrayList<>();
        int headerRowIdx = -1;
        for (int i = 0; i < rows.size() && headerRowIdx < 0; i++) {
            for (FieldWithCenter fw : rows.get(i)) {
                ColumnKind kind = classifyHeader(fw.field().inferText());
                if (kind != null) anchors.add(new ColumnAnchor(fw.x(), kind));
            }
            boolean hasName = anchors.stream().anyMatch(a -> a.kind() == ColumnKind.NAME);
            if (hasName && anchors.size() >= 2) headerRowIdx = i;
            else anchors.clear();
        }
        if (headerRowIdx < 0) {
            return List.of(parseByRegex(buildRawText(fields)));
        }

        List<ParsedOcrData> result = new ArrayList<>();
        for (int i = headerRowIdx + 1; i < rows.size(); i++) {
            EnumMap<ColumnKind, String> cells = new EnumMap<>(ColumnKind.class);
            for (FieldWithCenter fw : rows.get(i)) {
                ColumnKind kind = nearestColumn(anchors, fw.x());
                cells.putIfAbsent(kind, fw.field().inferText().trim());
            }

            String rawName = cells.get(ColumnKind.NAME);
            if (rawName == null) continue;
            String drugName = refineDrugName(rawName);
            if (drugName == null) continue;

            // ⚡ 수정: 숫자로 시작하지 않는 셀(주의사항 텍스트 등)은 값으로 인정하지 않음
            String dosageRaw = numericCell(cells.get(ColumnKind.DOSAGE));
            String unit = inferDosageUnit(drugName);
            String dosage = dosageRaw != null
                ? (dosageRaw.matches("\\d+(?:\\.\\d+)?") && unit != null ? dosageRaw + unit : dosageRaw)
                : null;
            Integer times = parseIntOrNull(numericCell(cells.get(ColumnKind.TIMES)));
            Integer days  = parseIntOrNull(numericCell(cells.get(ColumnKind.DAYS)));

            result.add(new ParsedOcrData(drugName, dosage, times, days));
        }
        result = dedupeByName(result);

        return result.isEmpty() ? List.of(parseByRegex(buildRawText(fields))) : result;
    }

    private ColumnKind classifyHeader(String text) {
        if (DRUG_NAME_KEYWORDS.stream().anyMatch(text::contains)) return ColumnKind.NAME;
        if (DOSAGE_KEYWORDS.stream().anyMatch(text::contains)) return ColumnKind.DOSAGE;
        if (TIMES_KEYWORDS.stream().anyMatch(text::contains)) return ColumnKind.TIMES;
        if (DAYS_KEYWORDS.stream().anyMatch(text::contains)) return ColumnKind.DAYS;
        return null;
    }

    private ColumnKind nearestColumn(List<ColumnAnchor> anchors, double x) {
        ColumnAnchor best = anchors.get(0);
        for (ColumnAnchor a : anchors) {
            if (Math.abs(a.x() - x) < Math.abs(best.x() - x)) best = a;
        }
        return best.kind();
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

    // ── 처방일 추출 ───────────────────────────────────────────────────────────

    // 처방일 우선 파싱, 없으면 조제일 fallback
    private LocalDate extractPrescriptionDate(String rawText) {
        LocalDate date = tryExtractDate(PRESCRIPTION_DATE_PATTERN, rawText);
        return date != null ? date : tryExtractDate(DISPENSE_DATE_PATTERN, rawText);
    }

    private LocalDate tryExtractDate(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (!m.find()) return null;
        try {
            int year  = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2).trim());
            int day   = Integer.parseInt(m.group(3).trim());
            return LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
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

    private Integer parseIntOrNull(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    // 숫자로 시작하는 셀만 값으로 통과 (표의 빈 칸에 주의사항/용법 텍스트가 들어오는 경우 방지)
    private String numericCell(String s) {
        return (s != null && s.matches("\\d.*")) ? s : null;
    }

    // 같은 약 이름이 두 번 이상 나오면(영수증의 상세+요약 섹션) 하나로 합친다.
    // 값이 있는 항목을 우선 유지한다.
    private List<ParsedOcrData> dedupeByName(List<ParsedOcrData> drugs) {
        LinkedHashMap<String, ParsedOcrData> byName = new LinkedHashMap<>();
        for (ParsedOcrData d : drugs) {
            ParsedOcrData prev = byName.get(d.drugName());
            if (prev == null || (isEmpty(prev) && !isEmpty(d))) {
                byName.put(d.drugName(), d);
            }
        }
        return new ArrayList<>(byName.values());
    }

    private boolean isEmpty(ParsedOcrData d) {
        return d.dosagePerTime() == null && d.timesPerDay() == null && d.totalDays() == null;
    }

    private record FieldWithCenter(NaverOcrApiResponse.Field field, double x, double y) {}

    private enum ColumnKind { NAME, DOSAGE, TIMES, DAYS }

    private record ColumnAnchor(double x, ColumnKind kind) {}
}