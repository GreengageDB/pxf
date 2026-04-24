-- ===================================================================
-- Validation for HTTP options
-- ===================================================================
-- This is expected to be run in the container
-- 
-- start_matchignore
-- m/^WARNING:  skipping ".*" --- cannot analyze this foreign table/
-- end_matchignore

CREATE FOREIGN DATA WRAPPER pxf_filter_push_down_fdw_http
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS (protocol 'system', mpp_execute 'all segments',
        pxf_protocol 'http'
    );

CREATE SERVER pxf_filter_push_down_server_http
    FOREIGN DATA WRAPPER pxf_filter_push_down_fdw_http;

CREATE USER MAPPING FOR CURRENT_USER SERVER pxf_filter_push_down_server_http;

CREATE FOREIGN TABLE test_filter(
    t0 text,
    a1 integer,
    b2 boolean,
    c3 numeric,
    d4 char(2),
    e5 varchar(2),
    x1 bpchar(2),
    x2 smallint,
    x3 bigint,
    x4 real,
    x5 float8,
    x6 bytea,
    x7 date,
    x8 time,
    x9 timestamp,
    x10 timestamp with time zone,
    x11 interval,
    x12 uuid,
    x13 json,
    x14 jsonb,
    x15 int2[],
    x16 int4[],
    x17 int8[],
    x18 bool[],
    x19 text[],
    x20 float4[],
    x21 float8[],
    x22 bytea[],
    x23 bpchar[],
    x24 varchar(2)[],
    x25 date[],
    x26 uuid[],
    x27 numeric[],
    x28 time[],
    x29 timestamp[],
    x30 timestamp with time zone[],
    x31 interval[],
    x32 json[],
    x33 jsonb[],
    filterValue text)
    SERVER pxf_filter_push_down_server_http
    OPTIONS (resource 'dummy_path', format 'filter', delimiter ',');

SELECT t0, a1 FROM test_filter;

-- Check writable table

CREATE FOREIGN DATA WRAPPER file_pxf_fdw_http
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS(protocol 'file', mpp_execute 'all segments',
        pxf_protocol 'http'
    );

CREATE SERVER pxf_file_server_http
    FOREIGN DATA WRAPPER file_pxf_fdw_http OPTIONS(
    config 'default');

CREATE USER MAPPING FOR CURRENT_USER SERVER pxf_file_server_http;

CREATE FOREIGN TABLE test_file2 (
    id int,
    descr text
) SERVER pxf_file_server_http
OPTIONS (resource 'fdw_file_http', format 'csv', delimiter ',');

INSERT INTO test_file2 select id, md5('hello'||id::text)as descr from generate_series(1,10) as id;

\! find /tmp/pxf/fdw_file_http -type f -name \[0-9]\* | xargs cat | LC_ALL=C sort -t',' -k1,1n

