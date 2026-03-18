CREATE FOREIGN DATA WRAPPER pxf_fdw_master_foreign_table_data_wrapper
    HANDLER pxf_fdw_handler
    VALIDATOR pxf_fdw_validator
    OPTIONS ( protocol 'system' );

CREATE SERVER pxf_fdw_master_foreign_table_server
    FOREIGN DATA WRAPPER pxf_fdw_master_foreign_table_data_wrapper;

CREATE USER MAPPING FOR current_user
    SERVER pxf_fdw_master_foreign_table_server;

CREATE FOREIGN TABLE pxf_fdw_master_foreign_table (id int, name text)
    SERVER pxf_fdw_master_foreign_table_server
    OPTIONS ( resource 'dummy_path', mpp_execute 'master', format 'filter', delimiter ',' );

SELECT * FROM pxf_fdw_master_foreign_table;
