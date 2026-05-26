package org.greenplum.pxf.plugins.jdbc.dialect;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.greenplum.pxf.plugins.jdbc.dialect.DefaultDatabaseDialect.DEFAULT_PRODUCT_NAME;
import static org.greenplum.pxf.plugins.jdbc.dialect.PostgresDatabaseDialect.POSTGRES_PRODUCT_NAME;

@Component
@Slf4j
public class DatabaseDialectProvider {

    private final Map<String, DatabaseDialect> dialectsByProductName;

    public DatabaseDialectProvider(Collection<DatabaseDialect> databaseDialects) {
        this.dialectsByProductName = databaseDialects.stream()
                .collect(Collectors.toMap(
                        dialect -> dialect.getName().toUpperCase(Locale.ROOT),
                        Function.identity())
                );
        if (!dialectsByProductName.containsKey(DEFAULT_PRODUCT_NAME)) {
            throw new IllegalStateException("Default database dialect {} is missing");
        }
        if (!dialectsByProductName.containsKey(POSTGRES_PRODUCT_NAME)) {
            throw new IllegalStateException("PostgreSql database dialect {} is missing");
        }
    }

    public DatabaseDialect get(String productName, boolean treatUnknownDbmsAsPostgreSql) {
        DatabaseDialect dialect = productName == null ? null : dialectsByProductName.get(productName.toUpperCase(Locale.ROOT));
        if (dialect == null) {
            dialect = treatUnknownDbmsAsPostgreSql
                    ? dialectsByProductName.get(POSTGRES_PRODUCT_NAME)
                    : dialectsByProductName.get(DEFAULT_PRODUCT_NAME);
        }
        log.debug("Database dialect '{}' is used", dialect.getName());
        return dialect;
    }
}
