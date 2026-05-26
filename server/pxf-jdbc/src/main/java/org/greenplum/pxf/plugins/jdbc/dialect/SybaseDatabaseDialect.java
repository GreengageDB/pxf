package org.greenplum.pxf.plugins.jdbc.dialect;

import org.springframework.stereotype.Component;

@Component
public class SybaseDatabaseDialect implements DatabaseDialect {

    @Override
    public String getName() {
        return "ADAPTIVE SERVER ENTERPRISE";
    }

    @Override
    public String buildSessionQuery(String key, String value) {
        return String.format("SET %s %s", key, value);
    }

    @Override
    public String wrapTimestampWithTZ(String val) {
        throw new UnsupportedOperationException(
                String.format("The database %s doesn't support the TIMESTAMP WITH TIME ZONE data type", getName()));
    }
}
