package com.osmshop.catalog;

import com.osmshop.auth.AuthApiException;
import com.osmshop.catalog.ManagerCatalogDtos.CategoryRequest;
import com.osmshop.catalog.ManagerCatalogDtos.ManagerCategoryView;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager category maintenance (UC-MNG-002). Categories are a flat list (no parent, by team decision).
 * Names are unique ignoring case. A category that still holds products cannot be archived or deleted.
 */
@Service
public class ManagerCategoryService {
    private static final String VIEW = """
            SELECT c.id, c.name, c.description, c.status,
                   count(p.id) AS product_count,
                   count(p.id) FILTER (WHERE p.status = 'ACTIVE') AS active_product_count,
                   c.created_at, c.updated_at
            FROM categories c LEFT JOIN products p ON p.category_id = c.id
            """;

    private final JdbcTemplate jdbc;
    private final CatalogAudit audit;

    public ManagerCategoryService(JdbcTemplate jdbc, CatalogAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<ManagerCategoryView> list(String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(VIEW + " GROUP BY c.id ORDER BY c.name, c.id", ManagerCategoryService::view);
        }
        return jdbc.query(VIEW + " WHERE c.status = ? GROUP BY c.id ORDER BY c.name, c.id",
                ManagerCategoryService::view, status);
    }

    public ManagerCategoryView get(long id) {
        return jdbc.query(VIEW + " WHERE c.id = ? GROUP BY c.id", ManagerCategoryService::view, id)
                .stream().findFirst().orElseThrow(ManagerCategoryService::notFound);
    }

    @Transactional
    public ManagerCategoryView create(long actorId, CategoryRequest request) {
        String name = request.name().trim();
        requireUniqueName(name, null);
        long id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO categories(name, description, status) VALUES (?, ?, 'ACTIVE') RETURNING id
                    """, Long.class, name, blankToNull(request.description()));
        } catch (DuplicateKeyException exception) {
            throw nameExists();
        }
        audit.record(actorId, "CATEGORY_CREATED", "CATEGORY", id, "name", name);
        return get(id);
    }

    @Transactional
    public ManagerCategoryView update(long actorId, long id, CategoryRequest request) {
        lock(id);
        String name = request.name().trim();
        requireUniqueName(name, id);
        try {
            jdbc.update("UPDATE categories SET name = ?, description = ?, updated_at = now() WHERE id = ?",
                    name, blankToNull(request.description()), id);
        } catch (DuplicateKeyException exception) {
            throw nameExists();
        }
        audit.record(actorId, "CATEGORY_UPDATED", "CATEGORY", id, "name", name);
        return get(id);
    }

    /**
     * ACTIVE / INACTIVE / ARCHIVED. INACTIVE hides the category and all its products from the shop.
     * ARCHIVED is only allowed once every product in it is archived or moved to another category.
     */
    @Transactional
    public ManagerCategoryView changeStatus(long actorId, long id, String status) {
        String previous = lock(id);
        if ("ARCHIVED".equals(status) && countProducts(id, true) > 0) throw hasProducts();
        if (!previous.equals(status)) {
            jdbc.update("UPDATE categories SET status = ?, updated_at = now() WHERE id = ?", status, id);
            audit.record(actorId, "CATEGORY_STATUS_CHANGED", "CATEGORY", id, "from", previous, "to", status);
        }
        return get(id);
    }

    /** Only an empty category can be deleted; otherwise products must be reassigned first. */
    @Transactional
    public void delete(long actorId, long id) {
        lock(id);
        if (countProducts(id, false) > 0) throw hasProducts();
        String name = jdbc.queryForObject("DELETE FROM categories WHERE id = ? RETURNING name", String.class, id);
        audit.record(actorId, "CATEGORY_DELETED", "CATEGORY", id, "name", name);
    }

    private String lock(long id) {
        return jdbc.queryForList("SELECT status FROM categories WHERE id = ? FOR UPDATE", String.class, id)
                .stream().findFirst().orElseThrow(ManagerCategoryService::notFound);
    }

    private long countProducts(long categoryId, boolean onlyUnarchived) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM products WHERE category_id = ?"
                + (onlyUnarchived ? " AND status <> 'ARCHIVED'" : ""), Long.class, categoryId);
        return count == null ? 0 : count;
    }

    /** Checked first so a duplicate does not abort the transaction; the unique index still guards races. */
    private void requireUniqueName(String name, Long exceptId) {
        Boolean taken = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM categories WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?)",
                Boolean.class, name, exceptId);
        if (Boolean.TRUE.equals(taken)) throw nameExists();
    }

    private static ManagerCategoryView view(ResultSet rs, int row) throws SQLException {
        return new ManagerCategoryView(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("status"), rs.getLong("product_count"), rs.getLong("active_product_count"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AuthApiException notFound() {
        return new AuthApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Không tìm thấy danh mục");
    }

    private static AuthApiException nameExists() {
        return new AuthApiException(HttpStatus.CONFLICT, "CATEGORY_NAME_EXISTS", "Tên danh mục đã tồn tại.");
    }

    private static AuthApiException hasProducts() {
        return new AuthApiException(HttpStatus.CONFLICT, "CATEGORY_HAS_PRODUCTS",
                "Danh mục vẫn còn sản phẩm. Hãy chuyển hoặc lưu trữ các sản phẩm trước.");
    }
}
