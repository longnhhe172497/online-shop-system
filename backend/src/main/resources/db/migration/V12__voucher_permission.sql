-- Managers maintain vouchers by default. Admin can change this grant at /internal/permissions.
INSERT INTO role_permissions(role,permission_code) VALUES ('MANAGER','MANAGER_VOUCHER_MANAGE');
UPDATE role_permission_sets SET version = version + 1, updated_at = now() WHERE role = 'MANAGER';
