package com.osmshop.catalog;

import com.osmshop.api.PageResponse;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    private final JdbcTemplate jdbc;

    public ProductService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<ProductResponse> listActiveProducts(int page, int size) {
        Long total = jdbc.queryForObject("""
                SELECT count(*) FROM products p JOIN categories c ON c.id = p.category_id
                WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE'
                """, Long.class);
        List<ProductResponse> items = jdbc.query("""
                SELECT p.id, p.sku, p.name, p.description, p.image_url, p.price,
                       c.name AS category_name, p.stock_on_hand - p.stock_reserved AS available_quantity
                FROM products p JOIN categories c ON c.id = p.category_id
                WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE'
                ORDER BY p.id LIMIT ? OFFSET ?
                """, (rs, rowNum) -> new ProductResponse(
                    rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                    rs.getString("description"), rs.getString("image_url"),
                    rs.getBigDecimal("price"), rs.getString("category_name"),
                    rs.getInt("available_quantity")), size, (long) page * size);
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }
}
