-- Module 5: permissions for refund operations, support tickets and transaction reporting.
INSERT INTO role_permissions(role, permission_code) VALUES
    ('MANAGER','MANAGER_REFUND_COMPLETE'),
    ('MANAGER','MANAGER_REPORT_VIEW'),
    ('SUPPORT','SUPPORT_TICKET_VIEW'),
    ('SUPPORT','SUPPORT_TICKET_REPLY')
ON CONFLICT DO NOTHING;
