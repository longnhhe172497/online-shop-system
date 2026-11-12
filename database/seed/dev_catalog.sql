-- DEV ONLY — FORME test catalog: 5 categories × 6 products (30), with photos.
-- Never run on a shared or production database.
--
-- Photos live in the repo (frontend/public/images/products, see CREDITS.md), so everyone who pulls
-- sees the same images. Load the catalog from the repository root (Git Bash):
--   docker exec -i online-shop-postgres psql -U online_shop -d online_shop < database/seed/dev_catalog.sql
-- or open this file in pgAdmin's Query Tool on database online_shop and run it.
--
-- Safe to run again: existing rows (same category name / SKU) are updated, stock already changed
-- during testing is kept. Matches the test data in the Stream B system test:
-- CAT-A Áo sơ mi, CAT-B Quần, P1 ST-AO-01, P2 ST-AO-02, P3 ST-QU-01, P4 ST-QU-02 (draft).
-- Price bands keep the price-filter cases valid: P2 is the only product under 500,000 ₫; P1 and P3 are
-- the only ones from 1,000,000 to 2,000,000 ₫; other shirts, trousers and accessories cost
-- 520,000–990,000 ₫; coats and dresses cost more than 2,000,000 ₫.

SET client_encoding = 'UTF8';
BEGIN;

INSERT INTO categories(name, description, status)
SELECT v.name, v.description, 'ACTIVE'
FROM (VALUES
    ('Áo sơ mi', 'Áo sơ mi và áo cotton.'),
    ('Quần', 'Quần âu, quần ống rộng, quần jeans.'),
    ('Phụ kiện', 'Túi, khăn, thắt lưng, mũ và kính.'),
    ('Áo khoác', 'Áo khoác, blazer, cardigan và măng tô.'),
    ('Váy & Đầm', 'Váy và đầm dạo phố, công sở, dự tiệc.')
) AS v(name, description)
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE lower(c.name) = lower(v.name));

CREATE TEMP TABLE seed_products (
    category text, sku text, name text, description text, image_url text, price numeric, stock int, status text
) ON COMMIT DROP;

INSERT INTO seed_products VALUES
    ('Áo sơ mi', 'ST-AO-01', 'Áo sơ mi xanh cobalt',
     'Áo sơ mi dáng rủ, cổ bẻ, hàng cúc dọc thân. Cotton poplin thoáng mát màu xanh cobalt đặc trưng của FORME.',
     '/images/products/st-ao-01.jpg', 1290000, 15, 'ACTIVE'),
    ('Áo sơ mi', 'ST-AO-02', 'Áo thun cotton trắng',
     'Áo thun cotton mịn cổ tròn, dáng vừa, mặc riêng hoặc lót trong áo khoác.',
     '/images/products/st-ao-02.jpg', 450000, 2, 'ACTIVE'),
    ('Áo sơ mi', 'ST-AO-03', 'Áo sơ mi xanh nhạt',
     'Áo sơ mi xanh nhạt dáng suông, tay dài xắn gấu, mặc cùng thắt lưng cói rất hợp.',
     '/images/products/st-ao-03.jpg', 890000, 10, 'ACTIVE'),
    ('Áo sơ mi', 'ST-AO-04', 'Áo sơ mi kẻ sọc',
     'Áo sơ mi kẻ sọc nhỏ, cổ đức, vải popeline mềm.',
     '/images/products/st-ao-04.jpg', 990000, 8, 'ACTIVE'),
    ('Áo sơ mi', 'ST-AO-05', 'Áo thun dài tay trắng',
     'Áo thun dài tay cổ tròn, cotton 2 chiều co giãn nhẹ.',
     '/images/products/st-ao-05.jpg', 590000, 20, 'ACTIVE'),
    ('Áo sơ mi', 'ST-AO-06', 'Áo sơ mi phối xanh trắng',
     'Áo sơ mi phối màu xanh trắng, dáng rộng, vai rơi.',
     '/images/products/st-ao-06.jpg', 790000, 9, 'ACTIVE'),
    ('Quần', 'ST-QU-01', 'Quần ống rộng xếp ly',
     'Quần ống rộng cạp cao, xếp ly trước, vải rủ đứng phom.',
     '/images/products/st-qu-01.jpg', 1890000, 0, 'ACTIVE'),
    ('Quần', 'ST-QU-02', 'Quần kaki nháp',
     'Quần kaki nâu ống đứng. Bản nháp, chưa mở bán.',
     '/images/products/st-qu-02.jpg', 790000, 5, 'DRAFT'),
    ('Quần', 'ST-QU-03', 'Quần âu xám',
     'Quần âu xám ống đứng, ly giữa sắc nét, hợp đi làm.',
     '/images/products/st-qu-03.jpg', 890000, 7, 'ACTIVE'),
    ('Quần', 'ST-QU-04', 'Quần ống suông hồng',
     'Quần ống suông màu hồng phấn, cạp chun sau thoải mái.',
     '/images/products/st-qu-04.jpg', 790000, 6, 'ACTIVE'),
    ('Quần', 'ST-QU-05', 'Quần jeans xanh',
     'Quần jeans xanh ống suông, denim dày dặn.',
     '/images/products/st-qu-05.jpg', 690000, 11, 'ACTIVE'),
    ('Quần', 'ST-QU-06', 'Quần âu đen',
     'Quần âu đen cạp cao, hai ly trước, ống đứng thanh lịch.',
     '/images/products/st-qu-06.jpg', 950000, 4, 'ACTIVE'),
    ('Phụ kiện', 'ST-PK-01', 'Túi tote vải canvas',
     'Túi tote vải canvas màu be, quai dài, ngăn trong có khóa.',
     '/images/products/st-pk-01.jpg', 690000, 14, 'ACTIVE'),
    ('Phụ kiện', 'ST-PK-02', 'Khăn choàng trắng',
     'Khăn choàng mỏng màu trắng, choàng cổ hoặc buộc túi đều đẹp.',
     '/images/products/st-pk-02.jpg', 520000, 12, 'ACTIVE'),
    ('Phụ kiện', 'ST-PK-03', 'Thắt lưng da nâu',
     'Thắt lưng da bò màu nâu, khóa kim loại bạc.',
     '/images/products/st-pk-03.jpg', 790000, 8, 'ACTIVE'),
    ('Phụ kiện', 'ST-PK-04', 'Mũ rộng vành đen',
     'Mũ rộng vành màu đen, dạ mềm, giữ phom tốt.',
     '/images/products/st-pk-04.jpg', 550000, 10, 'ACTIVE'),
    ('Phụ kiện', 'ST-PK-05', 'Túi đeo chéo da nâu',
     'Túi đeo chéo da mềm màu nâu, dây điều chỉnh độ dài.',
     '/images/products/st-pk-05.jpg', 990000, 5, 'ACTIVE'),
    ('Phụ kiện', 'ST-PK-06', 'Kính râm gọng trắng',
     'Kính râm gọng trắng, tròng xám chống tia UV400.',
     '/images/products/st-pk-06.jpg', 890000, 7, 'ACTIVE'),
    ('Áo khoác', 'ST-AK-01', 'Áo khoác da đen',
     'Áo khoác da đen dáng lửng, cổ bẻ, khóa kéo kim loại.',
     '/images/products/st-ak-01.jpg', 2490000, 6, 'ACTIVE'),
    ('Áo khoác', 'ST-AK-02', 'Áo cardigan len xám',
     'Cardigan len dệt kim màu xám, hàng cúc trước, ấm và nhẹ.',
     '/images/products/st-ak-02.jpg', 2190000, 12, 'ACTIVE'),
    ('Áo khoác', 'ST-AK-03', 'Áo blazer tweed xám',
     'Blazer tweed xám dáng suông, vai đứng, phối cùng áo croptop hoặc sơ mi.',
     '/images/hero/hero-blazer.jpg', 2890000, 7, 'ACTIVE'),
    ('Áo khoác', 'ST-AK-04', 'Bộ suit trắng tối giản',
     'Bộ suit trắng dáng suông, áo vest hai cúc đi kèm phần dưới cùng tông, chất liệu tuytsi cao cấp.',
     '/images/hero/hero-white-suit.jpg', 4590000, 3, 'ACTIVE'),
    ('Áo khoác', 'ST-AK-05', 'Áo cardigan len vàng',
     'Cardigan len vàng nghệ dáng rộng, bo tay và gấu áo.',
     '/images/products/st-ak-05.jpg', 2290000, 9, 'ACTIVE'),
    ('Áo khoác', 'ST-AK-06', 'Áo măng tô xám',
     'Măng tô dạ màu xám dáng dài, ve áo bản lớn.',
     '/images/products/st-ak-06.jpg', 2590000, 5, 'ACTIVE'),
    ('Váy & Đầm', 'ST-VD-01', 'Đầm cam đất',
     'Đầm dáng suông dài qua gối, màu cam đất, vải linen pha.',
     '/images/products/st-vd-01.jpg', 2290000, 5, 'ACTIVE'),
    ('Váy & Đầm', 'ST-VD-02', 'Đầm satin dáng dài',
     'Đầm satin dáng dài, cổ đổ, phù hợp dạ tiệc.',
     '/images/hero/hero-satin-dress.jpg', 3890000, 4, 'ACTIVE'),
    ('Váy & Đầm', 'ST-VD-03', 'Đầm hai dây chấm bi',
     'Đầm hai dây chấm bi xanh trắng, chân váy xòe nhẹ.',
     '/images/products/st-vd-03.jpg', 2390000, 10, 'ACTIVE'),
    ('Váy & Đầm', 'ST-VD-04', 'Đầm đỏ dáng dài',
     'Đầm đỏ dáng dài tay dài, eo chiết nhẹ.',
     '/images/products/st-vd-04.jpg', 3590000, 3, 'ACTIVE'),
    ('Váy & Đầm', 'ST-VD-05', 'Đầm suông linen',
     'Đầm suông linen màu tự nhiên. Không có ảnh: dùng để kiểm tra ảnh thay thế.',
     NULL, 2150000, 6, 'ACTIVE'),
    ('Váy & Đầm', 'ST-VD-06', 'Đầm xanh viền ren',
     'Đầm xanh dương viền ren trắng, tay ngắn, dáng chữ A.',
     '/images/products/st-vd-06.jpg', 2690000, 6, 'ACTIVE');

-- An earlier version of this catalog had a 7th coat (ST-AK-07). Stock history cannot be deleted,
-- so it is turned into the 6th dress instead of being removed.
UPDATE products SET sku = 'ST-VD-06', updated_at = now()
WHERE sku = 'ST-AK-07' AND NOT EXISTS (SELECT 1 FROM products WHERE upper(sku) = 'ST-VD-06');

INSERT INTO products(category_id, sku, name, description, image_url, price, stock_on_hand, status)
SELECT c.id, s.sku, s.name, s.description, s.image_url, s.price, s.stock, s.status
FROM seed_products s JOIN categories c ON lower(c.name) = lower(s.category)
WHERE NOT EXISTS (SELECT 1 FROM products p WHERE upper(p.sku) = upper(s.sku));

UPDATE products p
SET name = s.name, description = s.description, image_url = s.image_url, price = s.price,
    status = s.status, category_id = c.id, updated_at = now()
FROM seed_products s JOIN categories c ON lower(c.name) = lower(s.category)
WHERE upper(p.sku) = upper(s.sku)
  AND (p.name, p.description, p.image_url, p.price, p.status, p.category_id)
      IS DISTINCT FROM (s.name, s.description, s.image_url, s.price, s.status, c.id);

-- Starting stock as a manual adjustment, so the Warehouse screen shows where it came from.
-- Needs a Warehouse or Admin account; skipped otherwise.
INSERT INTO inventory_movements(product_id, movement_type, quantity_delta, stock_after, reason, performed_by)
SELECT p.id, 'MANUAL_ADJUSTMENT', p.stock_on_hand, p.stock_on_hand, 'Dữ liệu kiểm thử (dev seed)',
       (SELECT u.id FROM users u WHERE u.role IN ('WAREHOUSE', 'ADMIN') ORDER BY (u.role = 'WAREHOUSE') DESC, u.id LIMIT 1)
FROM products p
WHERE p.sku LIKE 'ST-%' AND p.stock_on_hand > 0
  AND EXISTS (SELECT 1 FROM users u WHERE u.role IN ('WAREHOUSE', 'ADMIN'))
  AND NOT EXISTS (SELECT 1 FROM inventory_movements m WHERE m.product_id = p.id);

COMMIT;

-- Every category should show 6 products
SELECT c.name AS category, count(p.id) AS products, count(p.id) FILTER (WHERE p.status = 'ACTIVE') AS on_sale
FROM categories c LEFT JOIN products p ON p.category_id = c.id AND p.status <> 'ARCHIVED'
WHERE c.name IN ('Áo sơ mi', 'Quần', 'Phụ kiện', 'Áo khoác', 'Váy & Đầm')
GROUP BY c.name ORDER BY c.name;
