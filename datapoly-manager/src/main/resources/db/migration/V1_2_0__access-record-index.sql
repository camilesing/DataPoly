-- Dashboard/statistics queries filter DATAPOLY_ACCESS_RECORD by create_time ranges and
-- by (api_id, create_time); the table previously carried no secondary index at all, so
-- every overview query and the daily cleanup DELETE full-scanned it.
CREATE INDEX `idx_access_record_create_time` ON `DATAPOLY_ACCESS_RECORD` (`create_time`);
CREATE INDEX `idx_access_record_api_time` ON `DATAPOLY_ACCESS_RECORD` (`api_id`, `create_time`);
