package com.piuda.callcare.domain.ocrresult.service;

import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OcrParser {

    // *로 시작하는 약 이름 우선 매칭 (예: "*아클펜정(아세클로페낙)")
    // 없으면 일반 약 이름 패턴으로 폴백
    private static final Pattern STARRED_DRUG_NAME_PATTERN = Pattern.compile(
        "\\*([가-힣a-zA-Z0-9]+(?:정|캡슐|시럽|액|연고|크림|주사|산|패치))"
    );
    private static final Pattern DRUG_NAME_PATTERN = Pattern.compile(
        "([가-힣a-zA-Z0-9]+(?:정|캡슐|시럽|액|연고|크림|주사|산|패치))"
    );

    // "1일투여횟수2", "1일 투여 횟수 2", "1일 3회", "하루 2번" 등 실제 처방전 용어 우선
    private static final Pattern TIMES_PER_DAY_PATTERN = Pattern.compile(
        "1일\\s*투여\\s*횟수\\s*(\\d+)|1일\\s*복용\\s*횟수\\s*(\\d+)|1일\\s*(\\d+)\\s*회|하루\\s*(\\d+)\\s*(?:번|회)"
    );

    // "1회투약량1", "1회 투약량 1", "1회 1정" 등
    private static final Pattern DOSAGE_PER_TIME_PATTERN = Pattern.compile(
        "1회\\s*투약량\\s*(\\d+)\\s*(정|캡슐|ml|mg|g)?|1회\\s*(\\d+)\\s*(정|캡슐|ml|mg|g)|한\\s*번에\\s*(\\d+)\\s*(정|캡슐)"
    );

    // "총투약일수5", "총 투약 일수 5", "7일분" 등. "투약일수"를 최우선으로 매칭해
    // 영수증의 다른 숫자("투약일수\n조제일자... 5" 같은 노이즈)에 안 걸리도록 함
    private static final Pattern TOTAL_DAYS_PATTERN = Pattern.compile(
        "총\\s*투약\\s*일수\\s*(\\d+)|총\\s*복용\\s*일수\\s*(\\d+)|(\\d+)\\s*일(?:분|치|간)"
    );

    public ParsedOcrData parse(String rawText, OcrType ocrType) {
        return switch (ocrType) {
            case PRESCRIPTION -> parsePrescription(rawText);
            case DRUG_BAG, DRUG_BOX -> parsePackage(rawText);
        };
    }

    private ParsedOcrData parsePrescription(String rawText) {
        String drugName = extractDrugName(rawText);
        Integer timesPerDay = extractTimesPerDay(rawText);
        String dosagePerTime = extractDosagePerTime(rawText);
        Integer totalDays = extractTotalDays(rawText);
        return new ParsedOcrData(drugName, dosagePerTime, timesPerDay, totalDays);
    }

    // 약봉투/약곽은 동일한 패턴으로 파싱
    private ParsedOcrData parsePackage(String rawText) {
        return parsePrescription(rawText);
    }

    private String extractDrugName(String text) {
        Matcher starred = STARRED_DRUG_NAME_PATTERN.matcher(text);
        if (starred.find()) {
            return starred.group(1);
        }
        Matcher matcher = DRUG_NAME_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private Integer extractTimesPerDay(String text) {
        Matcher matcher = TIMES_PER_DAY_PATTERN.matcher(text);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return Integer.parseInt(matcher.group(i));
                }
            }
        }
        return null;
    }

    private String extractDosagePerTime(String text) {
        Matcher matcher = DOSAGE_PER_TIME_PATTERN.matcher(text);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i += 2) {
                String amount = matcher.group(i);
                String unit = (i + 1 <= matcher.groupCount()) ? matcher.group(i + 1) : null;
                if (amount != null) {
                    return amount + (unit != null ? unit : inferUnitOnly(text));
                }
            }
        }
        return inferDefaultDosage(text);
    }

    private String inferUnitOnly(String text) {
        if (text.contains("정")) return "정";
        if (text.contains("캡슐")) return "캡슐";
        if (text.contains("시럽") || text.contains("액")) return "ml";
        return "단위";
    }

    private String inferDefaultDosage(String text) {
        if (text.contains("정") || text.contains("캡슐")) return "1정";
        if (text.contains("시럽") || text.contains("액")) return "1ml";
        return "1단위";
    }

    private Integer extractTotalDays(String text) {
        Matcher matcher = TOTAL_DAYS_PATTERN.matcher(text);
        if (matcher.find()) {
            for (int i = 1; i <= matcher.groupCount(); i++) {
                if (matcher.group(i) != null) {
                    return Integer.parseInt(matcher.group(i));
                }
            }
        }
        return null;
    }
}