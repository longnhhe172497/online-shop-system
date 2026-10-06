package com.osmshop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DatabaseMigrationTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationCreatesCoreTablesAndDefaultSettings() {
        Integer tableCount = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN
                ('users','products','orders','order_items','payment_attempts',
                 'stock_reservations','inventory_movements','return_requests',
                 'support_tickets','audit_logs','system_settings')
                """, Integer.class);
        assertEquals(11, tableCount);
        assertEquals("15", jdbc.queryForObject(
                "SELECT value_text FROM system_settings WHERE setting_key = 'qr_expiry_minutes'", String.class));
        assertEquals("Asia/Ho_Chi_Minh", jdbc.queryForObject(
                "SELECT value_text FROM system_settings WHERE setting_key = 'reporting_time_zone'", String.class));
    }

    @Test
    void databaseRejectsNegativeOrOverReservedStock() {
        jdbc.update("INSERT INTO categories(name) VALUES ('Migration test category')");
        Long categoryId = jdbc.queryForObject("SELECT id FROM categories WHERE name = 'Migration test category'", Long.class);
        assertTrue(categoryId > 0);
        assertThrows(Exception.class, () -> jdbc.update("""
                INSERT INTO products(category_id,sku,name,price,stock_on_hand,stock_reserved)
                VALUES (?, 'TEST-OVER-RESERVED', 'Invalid stock', 100, 2, 3)
                """, categoryId));
    }
}
