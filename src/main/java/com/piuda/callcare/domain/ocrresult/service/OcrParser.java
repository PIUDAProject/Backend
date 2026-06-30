package com.piuda.callcare.domain.ocrresult.service;

import com.piuda.callcare.domain.ocrresult.dto.ParsedOcrData;
import com.piuda.callcare.domain.ocrresult.enums.OcrType;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class OcrParser {

    private static final Pattern DRUG_NAME_PATTERN = Pattern.compile(
            "([가-힣a-zA-Z0-9]+(?:정|캡슐|시럽|액|연고|크림|주사|산|패치))"
    );
    private static final Pattern TIMES_PER_DAY_PATTERN = Pattern.compile(
            "1일\\s*(\\d+)\\s*회|하루\\s*(\\d+)\\s*(?:번|회)|일\\s*(\\d+)\\s*회"
    );
    private static final Pattern DOSAGE_PER_TIME_PATTERN = Pattern.compile(
            "1회\\s*(\\d+)\\s*(정|캡슐|ml|mg|g)|한\\s*번에\\s*(\\d+)\\s*(정|캡슐)"
    );
    private static final Pattern TOTAL_DAYS_PATTERN = Pattern.compile(
            "(\\d+)\\s*일(?:분|치|간|)"
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
            String amount = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String unit = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            if (amount != null && unit != null) {
                return amount + unit;
            }
        }
        return inferDefaultDosage(text);
    }

    private String inferDefaultDosage(String text) {
        if (text.contains("정") || text.contains("캡슐")) return "1정";
        if (text.contains("시럽") || text.contains("액")) return "1ml";
        return "1단위";
    }

    private Integer extractTotalDays(String text) {
        Matcher matcher = TOTAL_DAYS_PATTERN.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
}
