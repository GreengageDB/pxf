package org.greenplum.pxf.plugins.jdbc.dialect;

import org.greenplum.pxf.plugins.jdbc.utils.oracle.OracleJdbcUtils;
import org.springframework.stereotype.Component;

@Component
public class OracleDatabaseDialect implements DatabaseDialect {
    @Override
    public String getName() {
        return "ORACLE";
    }

    @Override
    public String wrapDate(String val) {
        return "to_date('" + val + "', 'YYYY-MM-DD')";
    }

    @Override
    public String wrapDateWithTime(String val) {
        String valStr = String.valueOf(val);
        int index = valStr.lastIndexOf('.');
        if (index != -1) {
            valStr = valStr.substring(0, index);
        }
        return "to_date('" + valStr + "', 'YYYY-MM-DD HH24:MI:SS')";
    }

    @Override
    public String wrapTimestamp(String val) {
        return "to_timestamp('" + val + "', 'YYYY-MM-DD HH24:MI:SS.FF')";
    }

    /**
     * Convert Postgres timestamp with time zone string to the appropriate Oracle timestamp with time zone string format.
     */
    @Override
    public String wrapTimestampWithTZ(String val) {
        return "to_timestamp_tz('" + val + "', 'YYYY-MM-DD HH24:MI:SS.FFTZH:TZM')";
    }

    @Override
    public String buildSessionQuery(String key, String value) {
        return OracleJdbcUtils.buildSessionQuery(key, value);
    }
}
