-- admin 出厂口令变更（v1.4.1）：123456 → DataPoly@123456（bcrypt，仍沿用行内原盐，salt 列不动）。
-- where 限定历史出厂哈希：只迁移从未改过密码的部署，用户已改过的密码不会被覆盖；
-- 已经通过 DATAPOLY_ADMIN_PASSWORD 覆盖的部署哈希同样不匹配、自然跳过。
-- 目标哈希与 AdminPasswordInitializer#DEFAULT_ADMIN_BCRYPT_HASH 一致，该常量据此识别新出厂口令。
UPDATE `DATAPOLY_SYSTEM_USER`
SET `password` = '$2a$10$eUanVjvzV27BBxAb4zuBCuA7KW1vpxq9a0K0mzIp28yjiH8H/zmxi'
WHERE `username` = 'admin'
  AND `password` = '$2a$10$eUanVjvzV27BBxAb4zuBCugwnngHkRZ7ZB4iI5tdx9ETJ2tnXJJDy';
