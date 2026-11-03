package com.osmshop.catalog;

import com.osmshop.api.PageResponse;
import com.osmshop.auth.AuthApiException;
import com.osmshop.catalog.ManagerCatalogDtos.CategoryOption;
import com.osmshop.catalog.ManagerCatalogDtos.ManagerProductView;
import com.osmshop.catalog.ManagerCatalogDtos.ProductRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manager product maintenance (UC-MNG-001). New products start as DRAFT with zero stock.
 * Stock is never written here: Warehouse owns stock_on_hand and stock_reserved.
 * Products are never hard-deleted because orders and carts reference them; ARCHIVED replaces delete.
 */
@Service
public class ManagerProductService {
    private static final String VIEW = """
            SELECT p.id, p.sku, p.name, p.description, p.image_url, p.price, p.category_id,
                   c.name AS category_name, c.status AS category_status, p.status,
                   p.stock_on_hand, p.stock_reserved, p.created_at, p.updated_at
            FROM products p JOIN categories c ON c.id = p.category_id
            """;

    private final JdbcTemplate jdbc;
    private final CatalogAudit audit;

    public ManagerProductService(JdbcTemplate jdbc, CatalogAudit audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** All statuses, newest first. q matches name or SKU (wildcards are literal). */
    public PageResponse<ManagerProductView> list(String q, Long categoryId, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE true");
        List<Object> args = new ArrayList<>();
        String term = q == null ? "" : q.trim();
        if (!term.isEmpty()) {
            where.append(" AND (p.name ILIKE ? ESCAPE '\\' OR p.sku ILIKE ? ESCAPE '\\')");
            String like = "%" + escapeLike(term) + "%";
            args.add(like);
            args.add(like);
        }
        if (categoryId != null) {
            where.append(" AND p.category_id = ?");
            args.add(categoryId);
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND p.status = ?");
            args.add(status);
        }
        Long total = jdbc.queryForObject(
                "SELECT count(*) FROM products p JOIN categories c ON c.id = p.category_id" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<ManagerProductView> items = jdbc.query(
                VIEW + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?",
                ManagerProductService::view, pageArgs.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public ManagerProductView get(long id) {
        return jdbc.query(VIEW + " WHERE p.id = ?", ManagerProductService::view, id).stream().findFirst()
                .orElseThrow(ManagerProductService::productNotFound);
    }

    public List<CategoryOption> categoryOptions() {
        return jdbc.query("""
                SELECT id, name, status FROM categories WHERE status <> 'ARCHIVED' ORDER BY name, id
                """, (rs, row) -> new CategoryOption(rs.getLong("id"), rs.getString("name"), rs.getString("status")));
    }

    @Transactional
    public ManagerProductView create(long actorId, ProductRequest request) {
        String sku = normalizeSku(request.sku());
        lockUsableCategory(request.categoryId());
        requireUniqueSku(sku, null);
        long id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO products(category_id, sku, name, description, image_url, price, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'DRAFT') RETURNING id
                    """, Long.class, request.categoryId(), sku, request.name().trim(),
                    blankToNull(request.description()), blankToNull(request.imageUrl()), request.price());
        } catch (DuplicateKeyException exception) {
            throw skuExists();
        }
        audit.record(actorId, "PRODUCT_CREATED", "PRODUCT", id, "sku", sku, "status", "DRAFT");
        return get(id);
    }

    @Transactional
    public ManagerProductView update(long actorId, long id, ProductRequest request) {
        String status = lockProduct(id);
        String sku = normalizeSku(request.sku());
        String categoryStatus = lockUsableCategory(request.categoryId());
        // A live product must stay in a live category, otherwise it would silently vanish from the shop.
        if ("ACTIVE".equals(status) && !"ACTIVE".equals(categoryStatus)) throw categoryNotActive();
        requireUniqueSku(sku, id);
        try {
            jdbc.update("""
                    UPDATE products SET category_id = ?, sku = ?, name = ?, description = ?, image_url = ?,
                                        price = ?, updated_at = now()
                    WHERE id = ?
                    """, request.categoryId(), sku, request.name().trim(), blankToNull(request.description()),
                    blankToNull(request.imageUrl()), request.price(), id);
        } catch (DuplicateKeyException exception) {
            throw skuExists();
        }
        audit.record(actorId, "PRODUCT_UPDATED", "PRODUCT", id, "sku", sku,
                "price", request.price().toPlainString());
        return get(id);
    }

    /** DRAFT / ACTIVE / INACTIVE / ARCHIVED. Publishing (ACTIVE) requires an ACTIVE category. */
    @Transactional
    public ManagerProductView changeStatus(long actorId, long id, String status) {
        String previous = lockProduct(id);
        if ("ACTIVE".equals(status)) {
            Long categoryId = jdbc.queryForObject("SELECT category_id FROM products WHERE id = ?", Long.class, id);
            if (!"ACTIVE".equals(lockCategory(categoryId))) throw categoryNotActive();
        }
        if (!previous.equals(status)) {
            jdbc.update("UPDATE products SET status = ?, updated_at = now() WHERE id = ?", status, id);
            audit.record(actorId, "PRODUCT_STATUS_CHANGED", "PRODUCT", id, "from", previous, "to", status);
        }
        return get(id);
    }

    private String lockProduct(long id) {
        return jdbc.queryForList("SELECT status FROM products WHERE id = ? FOR UPDATE", String.class, id)
                .stream().findFirst().orElseThrow(ManagerProductService::productNotFound);
    }

    /** Locks against a concurrent archive; returns the category status or null when it does not exist. */
    private String lockCategory(Long categoryId) {
        return jdbc.queryForList("SELECT status FROM categories WHERE id = ? FOR SHARE", String.class, categoryId)
                .stream().findFirst().orElse(null);
    }

    private String lockUsableCategory(Long categoryId) {
        String status = lockCategory(categoryId);
        if (status == null || "ARCHIVED".equals(status)) {
            throw new AuthApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CATEGORY_INVALID",
                    "Danh mục không tồn tại hoặc đã được lưu trữ.");
        }
        return status;
    }

    /** Checked first so a duplicate does not abort the transaction; the unique index still guards races. */
    private void requireUniqueSku(String sku, Long exceptId) {
        Boolean taken = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM products WHERE upper(sku) = ? AND id IS DISTINCT FROM ?)",
                Boolean.class, sku, exceptId);
        if (Boolean.TRUE.equals(taken)) throw skuExists();
    }

    private static ManagerProductView view(ResultSet rs, int row) throws SQLException {
        int onHand = rs.getInt("stock_on_hand");
        int reserved = rs.getInt("stock_reserved");
        return new ManagerProductView(rs.getLong("id"), rs.getString("sku"), rs.getString("name"),
                rs.getString("description"), rs.getString("image_url"), rs.getBigDecimal("price"),
                rs.getLong("category_id"), rs.getString("category_name"), rs.getString("category_status"),
                rs.getString("status"), onHand, reserved, onHand - reserved,
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class));
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static AuthApiException productNotFound() {
        return new AuthApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Không tìm thấy sản phẩm");
    }

    private static AuthApiException skuExists() {
        return new AuthApiException(HttpStatus.CONFLICT, "SKU_ALREADY_EXISTS", "Mã SKU đã được dùng cho sản phẩm khác.");
    }

    private static AuthApiException categoryNotActive() {
        return new AuthApiException(HttpStatus.CONFLICT, "CATEGORY_NOT_ACTIVE",
                "Danh mục chưa hoạt động nên sản phẩm không thể mở bán.");
    }
}
