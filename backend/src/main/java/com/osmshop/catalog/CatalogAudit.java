package com.osmshop.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Writes Manager catalog changes to the append-only audit trail. */
@Component
class CatalogAudit {
    private final JdbcTemplate jdbc;

    CatalogAudit(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** details: alternating key/value pairs, e.g. "sku", "ABC-1". Null values are stored as JSON null. */
    void record(long actorId, String action, String entityType, long entityId, String... details) {
        Map<String, String> pairs = new LinkedHashMap<>();
        for (int i = 0; i + 1 < details.length; i += 2) pairs.put(details[i], details[i + 1]);
        List<Object> args = new ArrayList<>(List.of(actorId, action, entityType, Long.toString(entityId)));
        StringBuilder json = new StringBuilder("jsonb_build_object(");
        for (Map.Entry<String, String> pair : pairs.entrySet()) {
            if (args.size() > 4) json.append(',');
            json.append("?::text,?::text");
            args.add(pair.getKey());
            args.add(pair.getValue());
        }
        json.append(')');
        jdbc.update("INSERT INTO audit_logs(actor_id,action,entity_type,entity_id,details) VALUES (?,?,?,?,"
                + json + ")", args.toArray());
    }
}
