CREATE EXTENSION IF NOT EXISTS pxf_fdw;

2:!& python3 test/mock_server/pxf_mock.py > /tmp/pxf_mock.log 2>&1;

select pg_sleep(1);

CREATE FOREIGN DATA WRAPPER pxf_ext_v1
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS (protocol 'system', mpp_execute 'all segments',
        pxf_protocol 'http', pxf_port '5889', ext_protocol_version 'v1'
    );

CREATE SERVER pxf_ext_v1_server
    FOREIGN DATA WRAPPER pxf_ext_v1;

CREATE USER MAPPING FOR CURRENT_USER SERVER pxf_ext_v1_server;

CREATE FOREIGN TABLE test_t(
    t0 text,
    a1 integer
) SERVER pxf_ext_v1_server
OPTIONS (resource 'fdw_file', format 'csv', delimiter ',');

-- ANALYZE is not supported by pxf_fdw
set gp_autostats_mode = none;
set client_min_messages = info;

INSERT INTO test_t VALUES('hello world', 1);

1:!& curl http://localhost:5889/shutdown;

2<:

-- sleep to allow the shell properly close the files
1: select pg_sleep(1);

1:! cat /tmp/pxf_mock.log;

