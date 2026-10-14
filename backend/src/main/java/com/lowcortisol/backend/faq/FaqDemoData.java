package com.lowcortisol.backend.faq;

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
@ConditionalOnProperty(prefix = "app.faq", name = "demo-enabled", havingValue = "true")
public class FaqDemoData implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public FaqDemoData(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 735902185L);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM faqs)", Boolean.class))) return;
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/faqs.sql")).execute(dataSource);
    }
}
