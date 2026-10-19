package com.osmshop.catalog;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "management.health.mail.enabled=false")
@AutoConfigureMockMvc
@Transactional
class CatalogFlowTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void listHidesDraftProductsAndInactiveCategories() throws Exception {
        String t = token();
        long active = category(t + " active", "ACTIVE");
        long inactive = category(t + " inactive", "INACTIVE");
        product(active, t + " visible", 100000, "ACTIVE");
        product(active, t + " draft", 100000, "DRAFT");
        product(inactive, t + " hidden by category", 100000, "ACTIVE");

        mvc.perform(get("/api/products").param("q", t))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value(t + " visible"));
    }

    @Test
    void searchTreatsWildcardsLiterally() throws Exception {
        String t = token();
        long cat = category(t + " cat", "ACTIVE");
        product(cat, t + " 100% cotton", 100000, "ACTIVE");
        product(cat, t + " 1000 linen", 100000, "ACTIVE");
        product(cat, t + " a_b", 100000, "ACTIVE");
        product(cat, t + " axb", 100000, "ACTIVE");

        mvc.perform(get("/api/products").param("q", t + " 100%"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value(t + " 100% cotton"));
        mvc.perform(get("/api/products").param("q", t + " a_b"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].name").value(t + " a_b"));
    }

    @Test
    void filtersByCategoryAndPriceAndSorts() throws Exception {
        String t = token();
        long cat = category(t + " cat", "ACTIVE");
        long other = category(t + " other", "ACTIVE");
        product(cat, t + " cheap", 100000, "ACTIVE");
        product(cat, t + " middle", 200000, "ACTIVE");
        product(cat, t + " pricey", 300000, "ACTIVE");
        product(other, t + " elsewhere", 250000, "ACTIVE");

        mvc.perform(get("/api/products").param("q", t).param("categoryId", String.valueOf(cat))
                        .param("minPrice", "150000").param("sort", "price_desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].name").value(t + " pricey"))
                .andExpect(jsonPath("$.items[1].name").value(t + " middle"));

        mvc.perform(get("/api/products").param("q", t).param("maxPrice", "200000").param("sort", "price_asc"))
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].name").value(t + " cheap"));
    }

    @Test
    void detailReturnsVisibleProductAndHidesOthers() throws Exception {
        String t = token();
        long cat = category(t + " cat", "ACTIVE");
        long visible = product(cat, t + " visible", 120000, "ACTIVE");
        long draft = product(cat, t + " draft", 120000, "DRAFT");

        mvc.perform(get("/api/products/" + visible))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(t + " visible"))
                .andExpect(jsonPath("$.categoryId").value(cat))
                .andExpect(jsonPath("$.availableQuantity").value(5))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.avgRating").value(nullValue()));
        mvc.perform(get("/api/products/" + draft)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products/999999999")).andExpect(status().isNotFound());
    }

    @Test
    void categoriesListOnlyActiveOnes() throws Exception {
        String t = token();
        category(t + " shown", "ACTIVE");
        category(t + " hidden", "INACTIVE");

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(t + " shown")))
                .andExpect(content().string(not(containsString(t + " hidden"))));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        mvc.perform(get("/api/products").param("sort", "id; DROP TABLE products")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("minPrice", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("minPrice", "500").param("maxPrice", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products").param("categoryId", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/abc")).andExpect(status().isBadRequest());
    }

    private static String token() {
        return "t" + UUID.randomUUID().toString().substring(0, 8);
    }

    private long category(String name, String status) {
        return jdbc.queryForObject("INSERT INTO categories(name,status) VALUES (?,?) RETURNING id",
                Long.class, name, status);
    }

    private long product(long categoryId, String name, long price, String status) {
        return jdbc.queryForObject("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,stock_reserved,status)
                VALUES (?,?,?,?,7,2,?) RETURNING id
                """, Long.class, categoryId, "SKU-" + UUID.randomUUID(), name, price, status);
    }
}