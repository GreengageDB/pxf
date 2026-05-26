package org.greenplum.pxf.plugins.s3;

import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;

public class S3SelectDatabaseDialect implements DatabaseDialect {
    @Override
    public String getName() {
        return "S3 SELECT";
    }

    @Override
    public String wrapDate(String val) {
        return "TO_TIMESTAMP('" + val + "')";
    }

    @Override
    public String wrapDateWithTime(String val) {
        return wrapTimestamp(val);
    }

    @Override
    public String wrapTimestamp(String val) {
        return "TO_TIMESTAMP('" + val + "')";
    }
}
