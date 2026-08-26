-- =============================================================================
-- MealTime.BEDTIME 잔존 데이터 정리
-- =============================================================================
-- 배경
--   b72dae8(2026-07-03) ~ e88250e(2026-08-24) 사이의 코드는 timesPerDay >= 4 처방에
--   BEDTIME 스케줄을 만들었다. timesPerDay에는 @Min(1)만 있고 상한이 없어 4회 이상
--   등록이 그대로 통과했다.
--
--   e88250e에서 MealTime enum의 BEDTIME 상수를 제거했다. @Enumerated(STRING)이라
--   meal_time='BEDTIME'인 행이 남아 있으면 그 행을 읽는 순간
--   IllegalArgumentException이 나고 홈 조회·복약 로그 조회가 실패한다.
--
-- 왜 DELETE인가 (DINNER 변환이 아니라)
--   medication_log에 UNIQUE (medication_id, taken_date, meal_time)가 있다.
--   BEDTIME 행이 생기는 조건(4회 이상 처방)에서는 같은 약·같은 날 DINNER 행이
--   반드시 함께 존재하므로, DINNER로 변환하면 제약 위반이 난다.
--   medication_schedule도 같은 이유로 (medication, DINNER)가 중복된다.
--   4번째 복용은 현재 스키마로 표현할 수 없는 값이다(한 시간대 다회 복용 = 백로그).
--
-- 실행 순서: STEP 1 → (행이 있으면) STEP 2 → STEP 3 → STEP 4
-- =============================================================================


-- -----------------------------------------------------------------------------
-- STEP 1. 잔존 행 확인 — 먼저 이것만 돌린다
-- -----------------------------------------------------------------------------
SELECT 'medication_schedule' AS table_name, COUNT(*) AS bedtime_rows
  FROM medication_schedule WHERE meal_time = 'BEDTIME'
UNION ALL
SELECT 'medication_log', COUNT(*)
  FROM medication_log      WHERE meal_time = 'BEDTIME'
UNION ALL
SELECT 'call_log', COUNT(*)
  FROM call_log            WHERE meal_time = 'BEDTIME';

-- 셋 다 0이면 여기서 끝. 아래는 실행하지 않는다.
-- call_log는 0일 가능성이 높다 — 이전 CALL_MEAL_TIMES가 BEDTIME을 제외해
-- 스케줄러가 그 행을 만들 경로가 없었다.

-- 영향받는 약을 눈으로 확인하고 싶으면:
-- SELECT m.medication_id, m.drug_name, m.times_per_day, m.senior_id
--   FROM medication m
--   JOIN medication_schedule ms ON ms.medication_id = m.medication_id
--  WHERE ms.meal_time = 'BEDTIME';


-- -----------------------------------------------------------------------------
-- STEP 2. 백업 — 지우기 전에 원본을 남긴다 (되돌릴 수 있게)
-- -----------------------------------------------------------------------------
CREATE TABLE bak_20260824_medication_schedule_bedtime AS
    SELECT * FROM medication_schedule WHERE meal_time = 'BEDTIME';

CREATE TABLE bak_20260824_medication_log_bedtime AS
    SELECT * FROM medication_log WHERE meal_time = 'BEDTIME';

CREATE TABLE bak_20260824_call_log_bedtime AS
    SELECT * FROM call_log WHERE meal_time = 'BEDTIME';

-- 백업 행 수가 STEP 1과 일치하는지 확인
SELECT 'schedule' t, COUNT(*) FROM bak_20260824_medication_schedule_bedtime
UNION ALL SELECT 'log',  COUNT(*) FROM bak_20260824_medication_log_bedtime
UNION ALL SELECT 'call', COUNT(*) FROM bak_20260824_call_log_bedtime;


-- -----------------------------------------------------------------------------
-- STEP 3. 삭제
-- -----------------------------------------------------------------------------
-- medication_log는 medication_schedule을 참조하지 않고 medication을 직접 참조하므로
-- 삭제 순서에 제약이 없다.
START TRANSACTION;

DELETE FROM medication_schedule WHERE meal_time = 'BEDTIME';
DELETE FROM medication_log      WHERE meal_time = 'BEDTIME';
DELETE FROM call_log            WHERE meal_time = 'BEDTIME';

-- 삭제 행 수가 STEP 1과 일치하는지 확인한 뒤 COMMIT.
-- 예상과 다르면 ROLLBACK.
COMMIT;


-- -----------------------------------------------------------------------------
-- STEP 4. 재확인 — 셋 다 0이어야 한다
-- -----------------------------------------------------------------------------
SELECT 'medication_schedule' AS table_name, COUNT(*) AS bedtime_rows
  FROM medication_schedule WHERE meal_time = 'BEDTIME'
UNION ALL
SELECT 'medication_log', COUNT(*) FROM medication_log WHERE meal_time = 'BEDTIME'
UNION ALL
SELECT 'call_log',       COUNT(*) FROM call_log       WHERE meal_time = 'BEDTIME';


-- -----------------------------------------------------------------------------
-- 정리 — 배포 후 정상 동작을 확인하고 나서
-- -----------------------------------------------------------------------------
-- DROP TABLE bak_20260824_medication_schedule_bedtime;
-- DROP TABLE bak_20260824_medication_log_bedtime;
-- DROP TABLE bak_20260824_call_log_bedtime;
