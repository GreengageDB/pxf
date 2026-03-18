CREATE FOREIGN DATA WRAPPER pxf_fdw_master_foreign_table_data_wrapper
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'system', pxf_protocol 'http' );

CREATE SERVER pxf_fdw_master_foreign_table_server
    FOREIGN DATA WRAPPER pxf_fdw_master_foreign_table_data_wrapper;

CREATE USER MAPPING FOR current_user
    SERVER pxf_fdw_master_foreign_table_server;

CREATE FOREIGN TABLE pxf_fdw_master_foreign_table (id int, name text)
    SERVER pxf_fdw_master_foreign_table_server
    OPTIONS ( resource 'dummy_path', mpp_execute 'master' );

SELECT * FROM pxf_fdw_master_foreign_table;
