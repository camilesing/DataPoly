-- DATAPOLY_ACCESS_RECORD.api_id is written as a Long (AccessRecordEntity.apiId) and the
-- PostgreSQL baseline already declares it int8; the MySQL baseline kept varchar(50), which
-- forces implicit string conversions and makes the (api_id, create_time) index compare
-- strings. Align the column type. Rows are written by the entity as numeric values, so the
-- conversion is loss-free; nullability is kept to stay compatible with legacy rows.
ALTER TABLE `DATAPOLY_ACCESS_RECORD` MODIFY COLUMN `api_id` bigint DEFAULT NULL;
