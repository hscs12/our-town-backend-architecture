-- 1) 좌표화 상태 컬럼 추가
ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS geocode_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS geocode_attempts INT,
    ADD COLUMN IF NOT EXISTS geocode_source VARCHAR(20),
    ADD COLUMN IF NOT EXISTS geocode_updated_at TIMESTAMPTZ;

-- 기본값(기존 데이터 포함) 세팅
UPDATE rooms
SET geocode_status = CASE
                         WHEN (x IS NOT NULL AND x <> 0) AND (y IS NOT NULL AND y <> 0) THEN 'SUCCESS'
                         ELSE 'PENDING'
    END,
    geocode_attempts = COALESCE(geocode_attempts, 0);

ALTER TABLE rooms ALTER COLUMN geocode_status SET DEFAULT 'PENDING';
ALTER TABLE rooms ALTER COLUMN geocode_attempts SET DEFAULT 0;

-- 2) 좌표 컬럼: 0 → NULL 정리 + NULL 허용
ALTER TABLE rooms ALTER COLUMN x DROP NOT NULL;
ALTER TABLE rooms ALTER COLUMN y DROP NOT NULL;

UPDATE rooms SET x = NULL WHERE x = 0;
UPDATE rooms SET y = NULL WHERE y = 0;

-- 3) api_id 유니크 보장 (중복 있으면 먼저 정리)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ux_rooms_api_id'
    ) THEN
ALTER TABLE rooms ADD CONSTRAINT ux_rooms_api_id UNIQUE (api_id);
END IF;
END $$;

-- 4) 지오코딩 대상 조회용 인덱스(미좌표만 빠르게)
CREATE INDEX IF NOT EXISTS idx_rooms_geocode_pending
    ON rooms (geocode_status, geocode_attempts, id)
    WHERE (x IS NULL OR y IS NULL);

-- 5) SUCCESS 표기된 기존 좌표의 메타(선택)
UPDATE rooms
SET geocode_source = COALESCE(geocode_source, 'kakao'),
    geocode_updated_at = COALESCE(geocode_updated_at, NOW())
WHERE geocode_status = 'SUCCESS';
