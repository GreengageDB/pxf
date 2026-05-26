package org.greenplum.pxf.plugins.jdbc.dialect;

import org.springframework.stereotype.Component;

@Component
public class MySqlDatabaseDialect implements DatabaseDialect{
    @Override
    public String getName() {
        return "MYSQL";
    }

    @Override
    public String wrapDate(String val) {
        return "DATE('" + val + "')";
    }
}
