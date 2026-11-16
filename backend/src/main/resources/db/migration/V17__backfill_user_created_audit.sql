INSERT INTO audit_logs(actor_id, action, entity_type, entity_id, details, created_at)
SELECT u.id,
       'USER_CREATED',
       'USER',
       u.id::text,
       jsonb_build_object('source', 'HISTORICAL_BACKFILL'),
       u.created_at
FROM users u
WHERE NOT EXISTS (
    SELECT 1
    FROM audit_logs a
    WHERE a.action = 'USER_CREATED'
      AND a.entity_type = 'USER'
      AND a.entity_id = u.id::text
);
