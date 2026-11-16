package com.osmshop.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductServiceTests {
    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProductService products;

    @Test
    void returnsOnlyActiveProductsFromActiveCategories() {
        jdbc.update("INSERT INTO categories(name,status) VALUES ('API test category','ACTIVE')");
        Long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE name = 'API test category'", Long.class);
        jdbc.update("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,stock_reserved,status)
                VALUES (?,'API-TEST-ACTIVE','Visible item',120000,7,2,'ACTIVE'),
                       (?,'API-TEST-DRAFT','Hidden item',50000,4,0,'DRAFT')
                """, categoryId, categoryId);

        var result = products.listActiveProducts(0, 20);
        assertThat(result.items())
                .filteredOn(item -> item.name().equals("Visible item"))
                .singleElement()
                .satisfies(item -> assertThat(item.availableQuantity()).isEqualTo(5));
        assertThat(result.items()).noneMatch(item -> item.name().equals("Hidden item"));
    }
}
