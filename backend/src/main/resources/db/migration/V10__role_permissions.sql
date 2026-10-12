CREATE TABLE role_permission_sets (
    role varchar(30) PRIMARY KEY CHECK (role IN ('MANAGER','SUPPORT','WAREHOUSE','DELIVERY')),
    version integer NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by bigint REFERENCES users(id)
);

CREATE TABLE role_permissions (
    role varchar(30) NOT NULL REFERENCES role_permission_sets(role) ON DELETE CASCADE,
    permission_code varchar(60) NOT NULL,
    PRIMARY KEY (role, permission_code)
);

INSERT INTO role_permission_sets(role) VALUES ('MANAGER'),('SUPPORT'),('WAREHOUSE'),('DELIVERY');

-- Preserve the current role boundaries as the starting policy.
INSERT INTO role_permissions(role,permission_code) VALUES
    ('MANAGER','MANAGER_ORDER_VIEW'),
    ('MANAGER','MANAGER_ORDER_ASSIGN'),
    ('MANAGER','MANAGER_RETURN_DECIDE'),
    ('SUPPORT','SUPPORT_ORDER_VIEW'),
    ('SUPPORT','SUPPORT_ORDER_CONFIRM'),
    ('WAREHOUSE','WAREHOUSE_PACK_VIEW'),
    ('WAREHOUSE','WAREHOUSE_PACK'),
    ('WAREHOUSE','WAREHOUSE_INVENTORY_VIEW'),
    ('WAREHOUSE','WAREHOUSE_INVENTORY_ADJUST'),
    ('WAREHOUSE','WAREHOUSE_RETURN_INSPECT'),
    ('DELIVERY','DELIVERY_ASSIGNMENT_VIEW'),
    ('DELIVERY','DELIVERY_ATTEMPT_UPDATE');
