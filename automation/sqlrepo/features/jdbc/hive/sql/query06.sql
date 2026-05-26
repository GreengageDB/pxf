-- @description query06 for JDBC Hive query with timestamp with time zone filter
SELECT s1, n1, tmtz FROM pxf_jdbc_hive_types_table WHERE tmtz > '2024-09-12 15:15:00+03' ORDER BY n1, s1;
