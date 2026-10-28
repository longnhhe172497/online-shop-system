package com.osmshop.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.osmshop.auth.AuthService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class ManagerCatalogTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @Test
    void newProductStartsAsHiddenDraftWithZeroStockAndIsAudited() throws Exception {
        Staff manager = staff("MANAGER");
        long category = category("ACTIVE");
        String sku = "new-" + token();

        String created = send(manager, post("/api/manager/products"), """
                {"sku":"%s","name":"Áo thun","description":"  ","imageUrl":"","price":250000,"categoryId":%d}
                """.formatted(sku, category))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stockOnHand").value(0))
                .andExpect(jsonPath("$.availableQuantity").value(0))
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'PRODUCT_CREATED' AND entity_type = 'PRODUCT'
                  AND entity_id = ? AND actor_id = ?
                """, Integer.class, Long.toString(id), manager.id()));
        mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound());

        setStatus(manager, "/api/manager/products/" + id + "/status", "ACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'PRODUCT_STATUS_CHANGED' AND entity_id = ?
                  AND details ->> 'from' = 'DRAFT' AND details ->> 'to' = 'ACTIVE'
                """, Integer.class, Long.toString(id)));
    }

    @Test
    void rejectsDuplicateSkuInvalidInputAndUnusableCategories() throws Exception {
        Staff manager = staff("MANAGER");
        long category = category("ACTIVE");
        long archived = category("ARCHIVED");
        String sku = "dup-" + token();
        send(manager, post("/api/manager/products"), productJson(sku, "250000", category)).andExpect(status().isCreated());

        send(manager, post("/api/manager/products"), productJson(sku.toUpperCase(), "1000", category))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SKU_ALREADY_EXISTS"));
        send(manager, post("/api/manager/products"), productJson("ok-" + token(), "0", category))
                .andExpect(status().isBadRequest());
        send(manager, post("/api/manager/products"), productJson("bad sku!", "1000", category))
                .andExpect(status().isBadRequest());
        send(manager, post("/api/manager/products"), """
                {"sku":"ok-%s","name":" ","price":1000,"categoryId":%d}""".formatted(token(), category))
                .andExpect(status().isBadRequest());
        send(manager, post("/api/manager/products"), """
                {"sku":"ok-%s","name":"x","price":1000,"categoryId":%d,"imageUrl":"javascript:alert(1)"}"""
                .formatted(token(), category))
                .andExpect(status().isBadRequest());
        send(manager, post("/api/manager/products"), productJson("ok-" + token(), "1000", archived))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("CATEGORY_INVALID"));
        send(manager, post("/api/manager/products"), productJson("ok-" + token(), "1000", 999999999L))
                .andExpect(status().isUnprocessableContent());
        send(manager, patch("/api/manager/products/999999999"), productJson("ok-" + token(), "1000", category))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void editingNeverTouchesStockAndKeepsLiveProductsInLiveCategories() throws Exception {
        Staff manager = staff("MANAGER");
        long live = category("ACTIVE");
        long hidden = category("INACTIVE");
        long id = product(live, "ACTIVE", 9, 4);

        send(manager, patch("/api/manager/products/" + id), productJson("edit-" + token(), "199000.50", live))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(199000.50))
                .andExpect(jsonPath("$.stockOnHand").value(9))
                .andExpect(jsonPath("$.stockReserved").value(4))
                .andExpect(jsonPath("$.availableQuantity").value(5));

        send(manager, patch("/api/manager/products/" + id), productJson("edit-" + token(), "1000", hidden))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_ACTIVE"));

        long draft = product(hidden, "DRAFT", 0, 0);
        setStatus(manager, "/api/manager/products/" + draft + "/status", "ACTIVE")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NOT_ACTIVE"));
        setStatus(manager, "/api/manager/products/" + draft + "/status", "SOLD_OUT").andExpect(status().isBadRequest());
        setStatus(manager, "/api/manager/products/" + id + "/status", "ARCHIVED")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void listsAllStatusesWithFiltersAndCategoryOptions() throws Exception {
        Staff manager = staff("MANAGER");
        long category = category("ACTIVE");
        String name = jdbc.queryForObject("SELECT name FROM categories WHERE id = ?", String.class, category);
        long archivedCategory = category("ARCHIVED");
        product(category, "ACTIVE", 1, 0);
        long draft = product(category, "DRAFT", 0, 0);
        product(category, "ARCHIVED", 0, 0);

        mvc.perform(get("/api/manager/products").header("Authorization", manager.bearer())
                        .param("categoryId", Long.toString(category)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/api/manager/products").header("Authorization", manager.bearer())
                        .param("categoryId", Long.toString(category)).param("status", "DRAFT"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(draft))
                .andExpect(jsonPath("$.items[0].categoryName").value(name));
        mvc.perform(get("/api/manager/products").header("Authorization", manager.bearer()).param("status", "GONE"))
                .andExpect(status().isBadRequest());

        String options = mvc.perform(get("/api/manager/products/category-options")
                        .header("Authorization", manager.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(1, JsonPath.<java.util.List<?>>read(options, "$[?(@.id == " + category + ")]").size());
        assertEquals(0, JsonPath.<java.util.List<?>>read(options, "$[?(@.id == " + archivedCategory + ")]").size());
    }

    @Test
    void managerMaintainsCategories() throws Exception {
        Staff manager = staff("MANAGER");
        String name = "Category " + token();
        String created = send(manager, post("/api/manager/categories"), categoryJson(name))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.productCount").value(0))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        send(manager, post("/api/manager/categories"), categoryJson(name.toUpperCase()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_NAME_EXISTS"));
        send(manager, post("/api/manager/categories"), categoryJson(" ")).andExpect(status().isBadRequest());
        send(manager, patch("/api/manager/categories/" + id), categoryJson(name + " new"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value(name + " new"));

        long product = product(id, "ACTIVE", 3, 0);
        mvc.perform(get("/api/products/" + product)).andExpect(status().isOk());
        setStatus(manager, "/api/manager/categories/" + id + "/status", "INACTIVE")
                .andExpect(status().isOk()).andExpect(jsonPath("$.activeProductCount").value(1));
        mvc.perform(get("/api/products/" + product)).andExpect(status().isNotFound());

        setStatus(manager, "/api/manager/categories/" + id + "/status", "ARCHIVED")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_HAS_PRODUCTS"));
        mvc.perform(delete("/api/manager/categories/" + id).header("Authorization", manager.bearer()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATEGORY_HAS_PRODUCTS"));

        jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE id = ?", product);
        setStatus(manager, "/api/manager/categories/" + id + "/status", "ARCHIVED")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));

        long empty = category("ACTIVE");
        mvc.perform(delete("/api/manager/categories/" + empty).header("Authorization", manager.bearer()))
                .andExpect(status().isNoContent());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM categories WHERE id = ?", Integer.class, empty));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'CATEGORY_DELETED' AND entity_id = ?
                """, Integer.class, Long.toString(empty)));
    }

    @Test
    void onlyStaffWithTheGrantedPermissionCanManage() throws Exception {
        long category = category("ACTIVE");
        for (String role : new String[] {"CUSTOMER", "WAREHOUSE", "SUPPORT"}) {
            Staff other = staff(role);
            send(other, post("/api/manager/products"), productJson("x-" + token(), "1000", category))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/manager/categories").header("Authorization", other.bearer()))
                    .andExpect(status().isForbidden());
        }

        Staff manager = staff("MANAGER");
        mvc.perform(get("/api/manager/products").header("Authorization", manager.bearer())).andExpect(status().isOk());
        jdbc.update("DELETE FROM role_permissions WHERE role = 'MANAGER' AND permission_code = 'MANAGER_PRODUCT_MANAGE'");
        mvc.perform(get("/api/manager/products").header("Authorization", manager.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/categories").header("Authorization", manager.bearer()))
                .andExpect(status().isOk());
    }

    private ResultActions send(Staff staff, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mvc.perform(request.header("Authorization", staff.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions setStatus(Staff staff, String path, String status) throws Exception {
        return send(staff, patch(path), "{\"status\":\"" + status + "\"}");
    }

    private static String productJson(String sku, String price, long categoryId) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"Sản phẩm thử\",\"price\":" + price
                + ",\"categoryId\":" + categoryId + "}";
    }

    private static String categoryJson(String name) {
        return "{\"name\":\"" + name + "\",\"description\":\"Mô tả\"}";
    }

    private static String token() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long category(String status) {
        return jdbc.queryForObject("INSERT INTO categories(name,status) VALUES (?,?) RETURNING id",
                Long.class, "Manager test " + UUID.randomUUID(), status);
    }

    private long product(long categoryId, String status, int onHand, int reserved) {
        return jdbc.queryForObject("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,stock_reserved,status)
                VALUES (?,?,'Manager item',100000,?,?,?) RETURNING id
                """, Long.class, categoryId, "MGR-" + UUID.randomUUID(), onHand, reserved, status);
    }

    private record Staff(long id, String bearer) {}

    private Staff staff(String role) {
        Long id = jdbc.queryForObject("""
                INSERT INTO users(email,password_hash,full_name,role,status,verified_at)
                VALUES (?,?,'Catalog Staff',?,'ACTIVE',now()) RETURNING id
                """, Long.class, "manager-" + UUID.randomUUID() + "@example.com",
                passwords.encode("Password123"), role);
        String token = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO user_sessions(user_id,token_hash,expires_at) VALUES (?,?,now() + interval '1 hour')",
                id, AuthService.hash(token));
        return new Staff(id, "Bearer " + token);
    }
}
