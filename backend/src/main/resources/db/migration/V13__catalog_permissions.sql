-- Catalog management permissions for the Manager role (Stream B).
INSERT INTO role_permissions(role, permission_code) VALUES
    ('MANAGER', 'MANAGER_PRODUCT_MANAGE'),
    ('MANAGER', 'MANAGER_CATEGORY_MANAGE'),
    ('MANAGER', 'MANAGER_REVIEW_MODERATE');

UPDATE role_permission_sets SET version = version + 1, updated_at = now() WHERE role = 'MANAGER';