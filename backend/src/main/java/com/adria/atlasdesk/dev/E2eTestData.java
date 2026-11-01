package com.adria.atlasdesk.dev;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
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
    private final String publicDemoPassword;

    public E2eTestData(JdbcTemplate jdbc, PasswordEncoder passwords,
            @Value("${DEMO_PUBLIC_PASSWORD:AtlasDeskDemo2026!}") String publicDemoPassword) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.publicDemoPassword = publicDemoPassword;
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

    @Transactional
    public void seedPublicDemo() {
        jdbc.update("INSERT INTO organizations (name, require_approval, sla_low_hours, sla_medium_hours, sla_high_hours, created_at, updated_at) VALUES (?, true, 72, 24, 8, now(), now())",
                "AtlasDesk Public Demo");
        String passwordHash = passwords.encode(publicDemoPassword);
        insertUser(3, "AtlasDesk Demo Agent", "demo.agent@atlasdesk.test", passwordHash, "AGENT");
        insertUser(3, "AtlasDesk Demo Administrator", "demo-admin@atlasdesk.test",
                passwords.encode("e2e-only-" + java.util.UUID.randomUUID()), "ADMIN");
        insertUser(3, "AtlasDesk Demo Requester", "demo-requester@atlasdesk.test",
                passwords.encode("e2e-only-" + java.util.UUID.randomUUID()), "REQUESTER");
        jdbc.update("INSERT INTO assets (organization_id, asset_number, name, type, description, status, created_at, updated_at) VALUES (3, ?, ?, ?, ?, ?, now(), now())",
                "ATLAS-LT-001", "Notebook de atendimento", "Laptop",
                "Ativo fictício da demonstração pública.", "ACTIVE");
        jdbc.update("INSERT INTO assets (organization_id, asset_number, name, type, description, status, created_at, updated_at) VALUES (3, ?, ?, ?, ?, ?, now(), now())",
                "ATLAS-NW-001", "Roteador do escritório", "Network",
                "Ativo fictício da demonstração pública.", "ACTIVE");
        long agentId = jdbc.queryForObject("SELECT id FROM users WHERE organization_id = 3 AND email = ?",
                Long.class, "demo.agent@atlasdesk.test");
        long requesterId = jdbc.queryForObject("SELECT id FROM users WHERE organization_id = 3 AND email = ?",
                Long.class, "demo-requester@atlasdesk.test");
        long assetId = jdbc.queryForObject("SELECT id FROM assets WHERE organization_id = 3 AND asset_number = ?",
                Long.class, "ATLAS-LT-001");

        Instant createdAt = Instant.now().minusSeconds(86_400);
        long openTicketId = insertDemoTicket("[DEMO-ATL-001] Acesso VPN instável", "Acesso VPN instável",
                requesterId, null, assetId, "HIGH", "OPEN", createdAt, null, null, 0);
        insertDemoHistory(openTicketId, requesterId, "AtlasDesk Demo Requester", "CREATED", "Chamado criado",
                createdAt, 0);

        Instant inProgressAt = createdAt.plusSeconds(3_600);
        long inProgressId = insertDemoTicket("[DEMO-ATL-002] Notebook lento após atualização",
                "Notebook lento após atualização", requesterId, agentId, assetId, "MEDIUM", "IN_PROGRESS",
                createdAt, null, null, 2);
        insertDemoHistory(inProgressId, requesterId, "AtlasDesk Demo Requester", "CREATED", "Chamado criado",
                createdAt, 0);
        insertDemoHistory(inProgressId, agentId, "AtlasDesk Demo Agent", "ASSIGNED",
                "Responsável atribuído: AtlasDesk Demo Agent", inProgressAt, 1);
        insertDemoHistory(inProgressId, agentId, "AtlasDesk Demo Agent", "STARTED", "Atendimento iniciado",
                inProgressAt.plusSeconds(3_600), 2);

        Instant pendingAt = inProgressAt.plusSeconds(3_600);
        long pendingId = insertDemoTicket("[DEMO-ATL-003] Revisar alerta de autenticação",
                "Revisar alerta de autenticação", requesterId, agentId, assetId, "HIGH", "PENDING_APPROVAL",
                createdAt, null, null, 3);
        insertDemoHistory(pendingId, requesterId, "AtlasDesk Demo Requester", "CREATED", "Chamado criado",
                createdAt, 0);
        insertDemoHistory(pendingId, agentId, "AtlasDesk Demo Agent", "ASSIGNED",
                "Responsável atribuído: AtlasDesk Demo Agent", inProgressAt, 1);
        insertDemoHistory(pendingId, agentId, "AtlasDesk Demo Agent", "STARTED", "Atendimento iniciado",
                pendingAt, 2);
        insertDemoHistory(pendingId, agentId, "AtlasDesk Demo Agent", "RESOLUTION_REQUESTED",
                "Resolução enviada para aprovação", pendingAt.plusSeconds(3_600), 3);
    }

    private long insertDemoTicket(String title, String summary, long requesterId, Long assignedAgentId,
            long assetId, String priority, String status, Instant createdAt, Instant resolvedAt,
            Instant closedAt, long version) {
        Instant dueAt = createdAt.plusSeconds(priority.equals("HIGH") ? 28_800 : 86_400);
        return jdbc.queryForObject("""
                INSERT INTO tickets (organization_id, title, description, requester_id, assigned_agent_id, asset_id,
                    priority, status, created_at, updated_at, due_at, resolved_at, closed_at, version)
                VALUES (3, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, title, "Demonstração: " + summary + ". Dados fictícios.", requesterId,
                assignedAgentId, assetId, priority, status, Timestamp.from(createdAt), Timestamp.from(createdAt),
                Timestamp.from(dueAt), resolvedAt == null ? null : Timestamp.from(resolvedAt),
                closedAt == null ? null : Timestamp.from(closedAt), version);
    }

    private void insertDemoHistory(long ticketId, long actorId, String actorName, String eventType,
            String description, Instant occurredAt, long version) {
        jdbc.update("""
                INSERT INTO ticket_history_events (organization_id, ticket_id, actor_id, actor_name,
                    event_type, description, occurred_at, ticket_version)
                VALUES (3, ?, ?, ?, ?, ?, ?, ?)
                """, ticketId, actorId, actorName, eventType, description, Timestamp.from(occurredAt), version);
    }

    private void insertUser(long organizationId, String name, String email, String passwordHash, String role) {
        jdbc.update("INSERT INTO users (organization_id, name, email, password_hash, role, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, true, now(), now())",
                organizationId, name, email, passwordHash, role);
    }
}
