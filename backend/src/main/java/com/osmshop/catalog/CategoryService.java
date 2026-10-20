package com.osmshop.catalog;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    private final JdbcTemplate jdbc;

    public CategoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Flat list of storefront categories (no parent categories, by team decision). */
    public List<CategoryResponse> listActive() {
        return jdbc.query("""
                SELECT id, name, description FROM categories
                WHERE status = 'ACTIVE' ORDER BY name, id
                """, (rs, row) -> new CategoryResponse(
                        rs.getLong("id"), rs.getString("name"), rs.getString("description")));
    }
}