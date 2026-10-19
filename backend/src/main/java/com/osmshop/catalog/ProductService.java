package com.osmshop.catalog;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductService {
    /** Whitelisted ORDER BY clauses. User input never reaches the SQL text. */
    private static final Map<String, String> SORTS = Map.of(
            "newest", "p.created_at DESC, p.id DESC",
            "price_asc", "p.price ASC, p.id ASC",
            "price_desc", "p.price DESC, p.id DESC",
            "name", "p.name ASC, p.id ASC");
    private static final String VISIBLE_FROM =
            " FROM products p JOIN categories c ON c.id = p.category_id"
                    + " WHERE p.status = 'ACTIVE' AND c.status = 'ACTIVE'";

    private final JdbcTemplate jdbc;

    public ProductService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Kept for existing callers; same as search with no filter. */
    public PageResponse<ProductResponse> listActiveProducts(int page, int size) {
        return search(ProductFilter.none(), page, size);
    }

    public PageResponse<ProductResponse> search(ProductFilter filter, int page, int size) {
        if (filter.minPrice() != null && filter.maxPrice() != null
                && filter.minPrice().compareTo(filter.maxPrice()) > 0) {
            throw new AuthApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_RANGE", "Khoảng giá không hợp lệ");
        }
        StringBuilder where = new StringBuilder(VISIBLE_FROM);
        List<Object> args = new ArrayList<>();

        String q = filter.q() == null ? "" : filter.q().trim();
        if (!q.isEmpty()) {
            where.append(" AND (p.name ILIKE ? ESCAPE '\\' OR p.description ILIKE ? ESCAPE '\\'"
                    + " OR c.name ILIKE ? ESCAPE '\\')");
            String like = "%" + escapeLike(q) + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (filter.categoryId() != null) {
            where.append(" AND p.category_id = ?");
            args.add(filter.categoryId());
        }
        if (filter.minPrice() != null) {
            where.append(" AND p.price >= ?");
            args.add(filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            where.append(" AND p.price <= ?");
            args.add(filter.maxPrice());
        }

        Long total = jdbc.queryForObject("SELECT count(*)" + where, Long.class, args.toArray());
        String orderBy = SORTS.getOrDefault(filter.sort() == null ? "newest" : filter.sort(), SORTS.get("newest"));

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<ProductResponse> items = jdbc.query(
                "SELECT p.id, p.sku, p.name, p.description, p.image_url, p.price, c.name AS category_name,"
                        + " p.stock_on_hand - p.stock_reserved AS available_quantity"
                        + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                ProductService::mapProduct, pageArgs.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public ProductDetailResponse detail(long id) {
        return jdbc.query("""
                SELECT p.id, p.sku, p.name, p.description, p.image_url, p.price,
                       c.id AS category_id, c.name AS category_name,
                       p.stock_on_hand - p.stock_reserved AS available_quantity,
                       r.avg_rating, r.review_count
                FROM products p
                JOIN categories c ON c.id = p.category_id
                LEFT JOIN LATERAL (
                    SELECT round(avg(pr.rating), 1) AS avg_rating, count(*) AS review_count
                    FROM product_reviews pr JOIN order_items oi ON oi.id = pr.order_item_id
                    WHERE oi.product_id = p.id AND pr.status = 'VISIBLE'
                ) r ON true
                WHERE p.id = ? AND p.status = 'ACTIVE' AND c.status = 'ACTIVE'
                """, (rs, row) -> new ProductDetailResponse(
                        rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                        rs.getString("description"), rs.getString("image_url"), rs.getBigDecimal("price"),
                        rs.getLong("category_id"), rs.getString("category_name"),
                        rs.getInt("available_quantity"), rs.getBigDecimal("avg_rating"),
                        rs.getLong("review_count")), id)
                .stream().findFirst()
                .orElseThrow(() -> new AuthApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                        "Không tìm thấy sản phẩm"));
    }

    private static ProductResponse mapProduct(ResultSet rs, int row) throws SQLException {
        return new ProductResponse(
                rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                rs.getString("description"), rs.getString("image_url"),
                rs.getBigDecimal("price"), rs.getString("category_name"),
                rs.getInt("available_quantity"));
    }

    /** Makes %, _ and \ match literally inside ILIKE. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}