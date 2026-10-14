package com.lowcortisol.backend.faq;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FaqRepository {
    private static final String SELECT = "SELECT faq_id, question, answer, sort_order FROM faqs WHERE is_visible = TRUE";
    private final JdbcTemplate jdbc;
    private final RowMapper<FaqResponse> mapper = (rs, row) -> new FaqResponse(rs.getLong("faq_id"),
        rs.getString("question"), rs.getString("answer"), rs.getInt("sort_order"));

    public FaqRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    List<FaqResponse> list(String query) {
        if (query.isEmpty()) return jdbc.query(SELECT + " ORDER BY sort_order, faq_id", mapper);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.query(SELECT + " AND (question ILIKE ? ESCAPE '!' OR answer ILIKE ? ESCAPE '!') ORDER BY sort_order, faq_id",
            mapper, pattern, pattern);
    }

    Optional<FaqResponse> get(long id) {
        return jdbc.query(SELECT + " AND faq_id = ?", mapper, id).stream().findFirst();
    }
}
