package com.lowcortisol.backend.catalog;

import javax.sql.DataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.catalog", name = "demo-enabled", havingValue = "true")
public class CatalogDemoData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public CatalogDemoData(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Serialize demo initialization across instances without overwriting an existing catalog.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 735902184L);
        boolean occupied = Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM categories) OR EXISTS (SELECT 1 FROM products)", Boolean.class));
        if (occupied) return;
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/catalog.sql")).execute(dataSource);
    }
}
