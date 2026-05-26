package org.greenplum.pxf.plugins.jdbc.dialect;

import org.springframework.stereotype.Component;

@Component
public class DefaultDatabaseDialect implements DatabaseDialect {

    public static final String DEFAULT_PRODUCT_NAME = "OTHER";

    @Override
    public String getName() {
        return DEFAULT_PRODUCT_NAME;
    }
}
