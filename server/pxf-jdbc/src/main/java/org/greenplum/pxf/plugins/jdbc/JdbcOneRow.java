package org.greenplum.pxf.plugins.jdbc;

import lombok.Getter;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;

@Getter
public class JdbcOneRow extends OneRow {
    private final DatabaseDialect dialect;

    public JdbcOneRow(Object data, DatabaseDialect dialect) {
        super(data);
        this.dialect = dialect;
    }
}
