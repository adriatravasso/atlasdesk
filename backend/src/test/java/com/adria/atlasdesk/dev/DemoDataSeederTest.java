package com.adria.atlasdesk.dev;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("demo")
@TestPropertySource(properties = "DEMO_USER_PASSWORD=integration-only-test-password")
class DemoDataSeederTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void seedsRealisticDataWithValidWorkflowHistoryAndCanRunAgainWithoutDuplicates() {
        assertEquals(2, count("select count(*) from organizations where name in ('Atlas Technology', 'Nexus Solutions')"));
        assertEquals(6, count("select count(*) from users where email like '%@atlasdemo.local' or email like '%@nexusdemo.local'"));
        assertEquals(12, count("select count(*) from assets where asset_number like 'ATLAS-%' or asset_number like 'NEXUS-%'"));
        assertEquals(24, count("select count(*) from tickets where title like '[DEMO-%'"));
        assertTrue(count("select count(*) from tickets where status = 'PENDING_APPROVAL'") >= 1);
        assertTrue(count("select count(*) from tickets where status = 'CLOSED' and closed_at is not null") >= 1);
        assertTrue(count("select count(*) from ticket_history_events where event_type = 'RESOLUTION_REQUESTED'") >= 1);
        assertTrue(count("select count(*) from assets where status = 'INACTIVE' and (asset_number like 'ATLAS-%' or asset_number like 'NEXUS-%')") >= 2);

        String hash = jdbc.queryForObject(
                "select password_hash from users where email = 'admin@atlasdemo.local'", String.class);
        assertTrue(passwordEncoder.matches("integration-only-test-password", hash));

        Map<String, Integer> before = seededRecordCounts();
        seeder.run();
        assertEquals(before, seededRecordCounts());
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private Map<String, Integer> seededRecordCounts() {
        return Map.of(
                "organizations", count("select count(*) from organizations where name in ('Atlas Technology', 'Nexus Solutions')"),
                "users", count("select count(*) from users where email like '%@atlasdemo.local' or email like '%@nexusdemo.local'"),
                "assets", count("select count(*) from assets where asset_number like 'ATLAS-%' or asset_number like 'NEXUS-%'"),
                "tickets", count("select count(*) from tickets where title like '[DEMO-%'"),
                "history", count("select count(*) from ticket_history_events h join tickets t on t.id = h.ticket_id where t.title like '[DEMO-%'"));
    }
}
