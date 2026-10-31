package com.adria.atlasdesk.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("e2e")
public class E2eTestData {

    private static final String PASSWORD = "AtlasE2E!2026";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    public E2eTestData(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Transactional
    public void reset() {
        jdbc.execute("TRUNCATE TABLE ticket_history_events, tickets, assets, users, organizations RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO organizations (name, require_approval, sla_low_hours, sla_medium_hours, sla_high_hours, created_at, updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
                "AtlasDesk E2E", true, 72, 24, 4);
        jdbc.update("INSERT INTO organizations (name, require_approval, sla_low_hours, sla_medium_hours, sla_high_hours, created_at, updated_at) VALUES (?, ?, ?, ?, ?, now(), now())",
                "Other AtlasDesk E2E", false, 72, 24, 8);

        String hash = passwords.encode(PASSWORD);
        insertUser(1, "E2E Administrator", "admin@atlasdesk.test", hash, "ADMIN");
        insertUser(1, "E2E Agent", "agent@atlasdesk.test", hash, "AGENT");
        insertUser(1, "E2E Requester", "requester@atlasdesk.test", hash, "REQUESTER");
        insertUser(2, "Other Organization Admin", "admin@other-atlasdesk.test", hash, "ADMIN");
    }

    private void insertUser(long organizationId, String name, String email, String passwordHash, String role) {
        jdbc.update("INSERT INTO users (organization_id, name, email, password_hash, role, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, true, now(), now())",
                organizationId, name, email, passwordHash, role);
    }
}
