-- @description query01 for JDBC query with date wide range on with insert
--
-- start_matchsubs
--
-- # create a match/subs
--
-- m/WARNING.*cannot analyze this foreign table/
-- s/.*//
--
-- end_matchsubs
SET bytea_output=escape;
SELECT * FROM gpdb_writable_date_wide_range_target ORDER BY t1;

SELECT * FROM gpdb_types_with_date_wide_range ORDER BY t1;

INSERT INTO pxf_jdbc_writable_date_wide_range_on SELECT * FROM gpdb_types_with_date_wide_range;

SELECT * FROM gpdb_writable_date_wide_range_target ORDER BY t1;
