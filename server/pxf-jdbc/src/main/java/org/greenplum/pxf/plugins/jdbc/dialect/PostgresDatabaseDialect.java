package org.greenplum.pxf.plugins.jdbc.dialect;

import lombok.NonNull;
import org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class PostgresDatabaseDialect implements DatabaseDialect {

    public static final String POSTGRES_PRODUCT_NAME = "POSTGRESQL";

    @Override
    public String getName() {
        return POSTGRES_PRODUCT_NAME;
    }

    @Override
    public String wrapDate(String val) {
        return "date'" + val + "'";
    }

    @Override
    public String wrapDate(@NonNull LocalDate val, boolean isDateWideRange) {
        return wrapDate(isDateWideRange ? val.format(DateTimeEraFormatters.LOCAL_DATE_FORMATTER) : val.toString());
    }

    @Override
    public String wrapTimestamp(@NonNull LocalDateTime val, boolean isDateWideRange) {
        return wrapTimestamp(isDateWideRange ? val.format(DateTimeEraFormatters.LOCAL_DATE_TIME_FORMATTER) : val.toString());
    }

    @Override
    public String wrapTimestampWithTZ(String val) {
        return "'" + val + "'";
    }

    @Override
    public void setJsonObject(PreparedStatement statement, int index, Object value) throws SQLException {
        statement.setObject(index, value, Types.OTHER);
    }
}
