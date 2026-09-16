-- Dashboard/statistics queries filter datapoly_access_record by create_time ranges and
-- by (api_id, create_time); the table previously carried no secondary index at all, so
-- every overview query and the daily cleanup DELETE full-scanned it.
CREATE INDEX datapoly_access_record_create_time_idx ON datapoly_access_record (create_time);
CREATE INDEX datapoly_access_record_api_time_idx ON datapoly_access_record (api_id, create_time);
