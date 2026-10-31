package com.adria.atlasdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.adria.atlasdesk.asset.Asset;
import com.adria.atlasdesk.asset.AssetRepository;
import com.adria.atlasdesk.asset.AssetStatus;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TicketControllerTest {

    private static final String PASSWORD = "Ticket-Test-Password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private UserRepository users;

    @Autowired
    private AssetRepository assets;

    @Autowired
    private TicketRepository tickets;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void requesterCanCreateTicketWithOptionalAssetAndReadFilteredPageAndDetail() throws Exception {
        Organization organization = createOrganization("Ticket create", 72, 24, 8);
        User requester = createUser(organization, "Rae Requester", UserRole.REQUESTER);
        Asset asset = createAsset(organization, "LT-710", "Support Laptop");
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(requester, csrf);

        MvcResult created = mockMvc.perform(post("/api/tickets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"title":"  Laptop cannot connect  ","description":"Wi-Fi disconnects after login.","priority":"HIGH","assetId":%d}
                                """.formatted(asset.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.requesterId").value(requester.getId()))
                .andExpect(jsonPath("$.requesterName").value("Rae Requester"))
                .andExpect(jsonPath("$.assetId").value(asset.getId()))
                .andExpect(jsonPath("$.assetNumber").value("LT-710"))
                .andExpect(jsonPath("$.title").value("Laptop cannot connect"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.assignedAgentId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.resolvedAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.closedAt").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();
        JsonNode createdBody = objectMapper.readTree(created.getResponse().getContentAsByteArray());
        long ticketId = createdBody.get("id").asLong();
        Instant createdAt = Instant.parse(createdBody.get("createdAt").asText());
        Instant dueAt = Instant.parse(createdBody.get("dueAt").asText());
        assertThat(Duration.between(createdAt, dueAt)).isEqualTo(Duration.ofHours(8));
        assertThat(createdBody.get("updatedAt").asText()).isEqualTo(createdBody.get("createdAt").asText());

        mockMvc.perform(get("/api/tickets?status=OPEN&priority=HIGH&search=CONNECT&page=0&size=1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ticketId))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        mockMvc.perform(get("/api/tickets/{id}", ticketId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Wi-Fi disconnects after login."))
                .andExpect(jsonPath("$.requesterId").value(requester.getId()))
                .andExpect(jsonPath("$.assetNumber").value("LT-710"));
    }

    @Test
    void dashboardAggregatesOrganizationMetricsAndScopesRequesterTickets() throws Exception {
        Organization organization = createOrganization("Dashboard metrics");
        Organization foreignOrganization = createOrganization("Foreign dashboard metrics");
        User requester = createUser(organization, "Dashboard Requester", UserRole.REQUESTER);
        User otherRequester = createUser(organization, "Other Dashboard Requester", UserRole.REQUESTER);
        User agent = createUser(organization, "Dashboard Agent", UserRole.AGENT);
        User admin = createUser(organization, "Dashboard Admin", UserRole.ADMIN);
        User foreignAdmin = createUser(foreignOrganization, "Foreign Dashboard Admin", UserRole.ADMIN);

        Instant overdueAt = Instant.now().minus(Duration.ofHours(1));
        createDashboardTicket(organization, requester, "Open low", TicketPriority.LOW, TicketStatus.OPEN,
                overdueAt);
        createDashboardTicket(organization, requester, "In progress medium", TicketPriority.MEDIUM,
                TicketStatus.IN_PROGRESS, overdueAt);
        createDashboardTicket(organization, requester, "Pending high", TicketPriority.HIGH,
                TicketStatus.PENDING_APPROVAL, overdueAt);
        createDashboardTicket(organization, requester, "Resolved low", TicketPriority.LOW,
                TicketStatus.RESOLVED, overdueAt);
        createDashboardTicket(organization, requester, "Closed high", TicketPriority.HIGH,
                TicketStatus.CLOSED, overdueAt);
        createDashboardTicket(organization, otherRequester, "Other requester high", TicketPriority.HIGH,
                TicketStatus.OPEN, Instant.now().plus(Duration.ofDays(1)));
        createDashboardTicket(foreignOrganization, foreignAdmin, "Foreign overdue", TicketPriority.HIGH,
                TicketStatus.OPEN, overdueAt);

        createAsset(organization, "DASH-1", "Dashboard active one");
        createAsset(organization, "DASH-2", "Dashboard active two");
        Asset inactiveAsset = createAsset(organization, "DASH-3", "Dashboard inactive");
        inactiveAsset.updateProfile(inactiveAsset.getAssetNumber(), inactiveAsset.getName(),
                inactiveAsset.getType(), inactiveAsset.getDescription(), AssetStatus.INACTIVE);
        assets.saveAndFlush(inactiveAsset);
        createAsset(foreignOrganization, "FOREIGN-DASH-1", "Foreign active asset");

        CsrfFixture csrf = fetchCsrf();
        MockHttpSession requesterSession = login(requester, csrf);
        MockHttpSession agentSession = login(agent, csrf);
        MockHttpSession adminSession = login(admin, csrf);
        MockHttpSession foreignAdminSession = login(foreignAdmin, csrf);

        assertOrganizationDashboard(requesterSession, 5, 1, 1, 1, 1, 1, 3, 2, 1, 2, 2);
        assertOrganizationDashboard(agentSession, 6, 2, 1, 1, 1, 1, 3, 2, 1, 3, 2);
        assertOrganizationDashboard(adminSession, 6, 2, 1, 1, 1, 1, 3, 2, 1, 3, 2);
        assertOrganizationDashboard(foreignAdminSession, 1, 1, 0, 0, 0, 0, 1, 0, 0, 1, 1);
    }

    @Test
    void ticketHistoryIsTransactionalOrderedAndRestrictedToVisibleTickets() throws Exception {
        Organization organization = createOrganization("Ticket history", true);
        Organization foreignOrganization = createOrganization("Foreign ticket history");
        User requester = createUser(organization, "History Requester", UserRole.REQUESTER);
        User otherRequester = createUser(organization, "Other History Requester", UserRole.REQUESTER);
        User agent = createUser(organization, "History Agent", UserRole.AGENT);
        User admin = createUser(organization, "History Admin", UserRole.ADMIN);
        User foreignAdmin = createUser(foreignOrganization, "Foreign History Admin", UserRole.ADMIN);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession requesterSession = login(requester, csrf);
        MockHttpSession agentSession = login(agent, csrf);
        MockHttpSession adminSession = login(admin, csrf);
        MockHttpSession otherRequesterSession = login(otherRequester, csrf);
        MockHttpSession foreignAdminSession = login(foreignAdmin, csrf);

        MvcResult created = mockMvc.perform(post("/api/tickets").session(requesterSession)
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"title\":\"History lifecycle\",\"description\":\"Trace changes\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long ticketId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(get("/api/tickets/{id}/history", ticketId).session(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$[0].actorId").value(requester.getId()))
                .andExpect(jsonPath("$[0].actorName").value("History Requester"))
                .andExpect(jsonPath("$[0].description").value("Chamado criado"))
                .andExpect(jsonPath("$[0].occurredAt").isNotEmpty());

        mockMvc.perform(patch("/api/tickets/{id}/assign", ticketId).session(adminSession)
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"assignedAgentId\":" + agent.getId() + "}"))
                .andExpect(status().isOk());
        performTransition(ticketId, agentSession, csrf, "START").andExpect(status().isOk());
        performTransition(ticketId, agentSession, csrf, "START").andExpect(status().isConflict());
        mockMvc.perform(get("/api/tickets/{id}/history", ticketId).session(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        performTransition(ticketId, agentSession, csrf, "RESOLVE").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        performTransition(ticketId, adminSession, csrf, "REJECT").andExpect(status().isOk());
        performTransition(ticketId, agentSession, csrf, "RESOLVE").andExpect(status().isOk());
        performTransition(ticketId, adminSession, csrf, "APPROVE").andExpect(status().isOk());
        performTransition(ticketId, requesterSession, csrf, "CLOSE").andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/{id}/history", ticketId).session(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].eventType").value("CREATED"))
                .andExpect(jsonPath("$[1].eventType").value("ASSIGNED"))
                .andExpect(jsonPath("$[1].actorName").value("History Admin"))
                .andExpect(jsonPath("$[2].eventType").value("STARTED"))
                .andExpect(jsonPath("$[2].actorName").value("History Agent"))
                .andExpect(jsonPath("$[3].eventType").value("RESOLUTION_REQUESTED"))
                .andExpect(jsonPath("$[4].eventType").value("REJECTED"))
                .andExpect(jsonPath("$[5].eventType").value("RESOLUTION_REQUESTED"))
                .andExpect(jsonPath("$[6].eventType").value("APPROVED"))
                .andExpect(jsonPath("$[7].eventType").value("CLOSED"))
                .andExpect(jsonPath("$[7].actorId").value(requester.getId()));
        mockMvc.perform(get("/api/tickets/{id}/history", ticketId).session(otherRequesterSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tickets/{id}/history", ticketId).session(foreignAdminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void requesterSeesOnlyOwnTicketsWhileAgentAndAdminSeeOrganizationTickets() throws Exception {
        Organization organization = createOrganization("Ticket role scope");
        User requester = createUser(organization, "Requester One", UserRole.REQUESTER);
        User anotherRequester = createUser(organization, "Requester Two", UserRole.REQUESTER);
        User agent = createUser(organization, "Support Agent", UserRole.AGENT);
        User admin = createUser(organization, "Workspace Admin", UserRole.ADMIN);
        Ticket ownTicket = createTicket(organization, requester, "My open request", TicketPriority.LOW);
        Ticket otherTicket = createTicket(organization, anotherRequester, "Other private request", TicketPriority.MEDIUM);
        CsrfFixture csrf = fetchCsrf();

        MockHttpSession requesterSession = login(requester, csrf);
        mockMvc.perform(get("/api/tickets").session(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownTicket.getId()));
        mockMvc.perform(get("/api/tickets/{id}", otherTicket.getId()).session(requesterSession))
                .andExpect(status().isNotFound());

        MockHttpSession agentSession = login(agent, csrf);
        mockMvc.perform(get("/api/tickets").session(agentSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        MockHttpSession adminSession = login(admin, csrf);
        mockMvc.perform(get("/api/tickets").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void hidesForeignOrganizationTicketsAndRejectsForeignAssets() throws Exception {
        Organization ownOrganization = createOrganization("Ticket own org");
        Organization foreignOrganization = createOrganization("Ticket foreign org");
        User requester = createUser(ownOrganization, "Own Requester", UserRole.REQUESTER);
        User admin = createUser(ownOrganization, "Own Admin", UserRole.ADMIN);
        User foreignRequester = createUser(foreignOrganization, "Foreign Requester", UserRole.REQUESTER);
        Asset foreignAsset = createAsset(foreignOrganization, "FOREIGN-12", "Foreign laptop");
        Ticket ownTicket = createTicket(ownOrganization, admin, "Own organization ticket", TicketPriority.LOW);
        Ticket foreignTicket = createTicket(foreignOrganization, foreignRequester,
                "Foreign ticket title", TicketPriority.HIGH);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(requester, csrf);

        mockMvc.perform(get("/api/tickets/{id}", foreignTicket.getId()).session(session))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tickets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"title":"Cross tenant asset","description":"This must not be linked.","priority":"LOW","assetId":%d}
                                """.formatted(foreignAsset.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tickets").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        MockHttpSession adminSession = login(admin, csrf);
        mockMvc.perform(get("/api/tickets").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownTicket.getId()));
        mockMvc.perform(get("/api/tickets/{id}", foreignTicket.getId()).session(adminSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesTicketFieldsFiltersPaginationAndAuthentication() throws Exception {
        Organization organization = createOrganization("Ticket validation");
        User requester = createUser(organization, "Valid Requester", UserRole.REQUESTER);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(requester, csrf);

        mockMvc.perform(post("/api/tickets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"title":" ","description":"Issue description","priority":"LOW"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tickets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"title":"Missing priority","description":"Issue description"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tickets?page=-1&size=101").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tickets?status=UNKNOWN").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tickets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"title":"No asset needed","description":"This request has no associated asset.","priority":"LOW"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void assignedAgentHandlesTicketAndRequesterOrAdminCanCloseIt() throws Exception {
        Organization organization = createOrganization("Ticket transitions");
        User requester = createUser(organization, "Request Owner", UserRole.REQUESTER);
        User agent = createUser(organization, "Assigned Agent", UserRole.AGENT);
        User otherAgent = createUser(organization, "Other Agent", UserRole.AGENT);
        User admin = createUser(organization, "Ticket Admin", UserRole.ADMIN);
        Ticket requesterTicket = createTicket(organization, requester, "Requester flow", TicketPriority.HIGH);
        Ticket adminTicket = createTicket(organization, requester, "Admin flow", TicketPriority.LOW);
        Ticket agentAssignedTicket = createTicket(organization, requester, "Agent assignment", TicketPriority.MEDIUM);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession requesterSession = login(requester, csrf);
        MockHttpSession agentSession = login(agent, csrf);
        MockHttpSession otherAgentSession = login(otherAgent, csrf);
        MockHttpSession adminSession = login(admin, csrf);

        mockMvc.perform(patch("/api/tickets/{id}/assign", requesterTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"assignedAgentId\":" + agent.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgentId").value(agent.getId()));
        mockMvc.perform(post("/api/tickets/{id}/transition", requesterTicket.getId())
                        .session(otherAgentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"START\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tickets/{id}/transition", requesterTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"START\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(post("/api/tickets/{id}/transition", requesterTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"RESOLVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(post("/api/tickets/{id}/transition", requesterTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"CLOSE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/tickets/{id}/assign", requesterTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"assignedAgentId\":" + otherAgent.getId() + "}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/tickets/{id}/transition", requesterTicket.getId())
                        .session(requesterSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"CLOSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty())
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        mockMvc.perform(patch("/api/tickets/{id}/assign", adminTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"assignedAgentId\":" + agent.getId() + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/tickets/{id}/assign", agentAssignedTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"assignedAgentId\":" + otherAgent.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedAgentId").value(otherAgent.getId()));
        for (String command : new String[] {"START", "RESOLVE", "CLOSE"}) {
            mockMvc.perform(post("/api/tickets/{id}/transition", adminTicket.getId())
                            .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                            .contentType("application/json").content("{\"command\":\"" + command + "\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(tickets.findById(adminTicket.getId()).orElseThrow().getStatus()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void requiredApprovalLetsOnlySameOrganizationAdminApproveOrRejectAgentResolution() throws Exception {
        Organization organization = createOrganization("Ticket approval", true);
        Organization foreignOrganization = createOrganization("Foreign ticket approval");
        User requester = createUser(organization, "Approval Requester", UserRole.REQUESTER);
        User agent = createUser(organization, "Approval Agent", UserRole.AGENT);
        User admin = createUser(organization, "Approval Admin", UserRole.ADMIN);
        User foreignAdmin = createUser(foreignOrganization, "Foreign Approval Admin", UserRole.ADMIN);
        User foreignRequester = createUser(foreignOrganization, "Foreign Approval Requester", UserRole.REQUESTER);
        Ticket approvedTicket = createTicket(organization, requester, "Approve resolution", TicketPriority.HIGH);
        Ticket rejectedTicket = createTicket(organization, requester, "Reject resolution", TicketPriority.MEDIUM);
        Ticket adminTicket = createTicket(organization, requester, "Admin direct resolution", TicketPriority.LOW);
        Ticket foreignTicket = createTicket(foreignOrganization, foreignRequester,
                "Foreign approval target", TicketPriority.LOW);
        approvedTicket.assignTo(agent);
        rejectedTicket.assignTo(agent);
        tickets.saveAndFlush(approvedTicket);
        tickets.saveAndFlush(rejectedTicket);
        entityManager.createNativeQuery("update tickets set status = 'PENDING_APPROVAL' where id = :id")
                .setParameter("id", foreignTicket.getId()).executeUpdate();
        entityManager.clear();

        CsrfFixture csrf = fetchCsrf();
        MockHttpSession requesterSession = login(requester, csrf);
        MockHttpSession agentSession = login(agent, csrf);
        MockHttpSession adminSession = login(admin, csrf);
        MockHttpSession foreignAdminSession = login(foreignAdmin, csrf);

        for (Ticket ticket : new Ticket[] {approvedTicket, rejectedTicket}) {
            mockMvc.perform(post("/api/tickets/{id}/transition", ticket.getId())
                            .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                            .contentType("application/json").content("{\"command\":\"START\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
            mockMvc.perform(post("/api/tickets/{id}/transition", ticket.getId())
                            .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                            .contentType("application/json").content("{\"command\":\"RESOLVE\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                    .andExpect(jsonPath("$.resolvedAt").value(org.hamcrest.Matchers.nullValue()));
        }

        mockMvc.perform(post("/api/tickets/{id}/transition", approvedTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tickets/{id}/transition", approvedTicket.getId())
                        .session(requesterSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"REJECT\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/tickets/{id}/transition", foreignTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tickets/{id}/transition", approvedTicket.getId())
                        .session(foreignAdminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"APPROVE\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tickets/{id}/transition", approvedTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"APPROVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        mockMvc.perform(post("/api/tickets/{id}/transition", rejectedTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.resolvedAt").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(post("/api/tickets/{id}/transition", rejectedTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"APPROVE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/tickets/{id}/transition", adminTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"APPROVE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/tickets/{id}/transition", adminTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"START\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/{id}/transition", adminTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"RESOLVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void rejectsInvalidTransitionAssigneeAndCrossOrganizationOperations() throws Exception {
        Organization organization = createOrganization("Ticket invalid operations");
        Organization foreignOrganization = createOrganization("Ticket foreign operations");
        User requester = createUser(organization, "Request Owner", UserRole.REQUESTER);
        User admin = createUser(organization, "Ticket Admin", UserRole.ADMIN);
        User agent = createUser(organization, "Valid Agent", UserRole.AGENT);
        User inactiveAgent = createUser(organization, "Inactive Agent", UserRole.AGENT);
        User requesterCandidate = createUser(organization, "Not An Agent", UserRole.REQUESTER);
        User foreignAgent = createUser(foreignOrganization, "Foreign Agent", UserRole.AGENT);
        inactiveAgent.deactivate();
        users.saveAndFlush(inactiveAgent);
        Ticket ownTicket = createTicket(organization, requester, "Invalid flow", TicketPriority.MEDIUM);
        User foreignRequester = createUser(foreignOrganization, "Foreign Requester", UserRole.REQUESTER);
        Ticket foreignTicket = createTicket(foreignOrganization, foreignRequester,
                "Foreign operation target", TicketPriority.LOW);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession requesterSession = login(requester, csrf);
        MockHttpSession adminSession = login(admin, csrf);
        MockHttpSession agentSession = login(agent, csrf);

        mockMvc.perform(patch("/api/tickets/{id}/assign", ownTicket.getId())
                        .session(requesterSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"assignedAgentId\":" + agent.getId() + "}"))
                .andExpect(status().isForbidden());
        for (Long assigneeId : new Long[] {inactiveAgent.getId(), requesterCandidate.getId()}) {
            mockMvc.perform(patch("/api/tickets/{id}/assign", ownTicket.getId())
                            .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                            .contentType("application/json").content("{\"assignedAgentId\":" + assigneeId + "}"))
                    .andExpect(status().isConflict());
        }
        mockMvc.perform(patch("/api/tickets/{id}/assign", ownTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"assignedAgentId\":" + foreignAgent.getId() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tickets/{id}/assign", foreignTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"assignedAgentId\":" + agent.getId() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tickets/{id}/transition", foreignTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"START\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/tickets/{id}/assign", ownTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"assignedAgentId\":" + agent.getId() + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/{id}/transition", ownTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"RESOLVE\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/tickets/{id}/transition", ownTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"START\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tickets/{id}/transition", ownTicket.getId())
                        .session(agentSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"START\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/tickets/{id}/transition", ownTicket.getId())
                        .session(requesterSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"REOPEN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/tickets/{id}/transition", ownTicket.getId())
                        .session(requesterSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"CLOSE\"}"))
                .andExpect(status().isConflict());

        entityManager.createNativeQuery("update tickets set status = 'PENDING_APPROVAL' where id = :id")
                .setParameter("id", ownTicket.getId())
                .executeUpdate();
        entityManager.clear();
        mockMvc.perform(post("/api/tickets/{id}/transition", ownTicket.getId())
                        .session(adminSession).cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json").content("{\"command\":\"START\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void optimisticVersionRejectsAStaleConcurrentTicketUpdate() {
        Organization organization = createOrganization("Ticket optimistic version");
        User requester = createUser(organization, "Request Owner", UserRole.REQUESTER);
        User firstAgent = createUser(organization, "First Agent", UserRole.AGENT);
        User secondAgent = createUser(organization, "Second Agent", UserRole.AGENT);
        Ticket ticket = createTicket(organization, requester, "Concurrent assignment", TicketPriority.LOW);
        Ticket staleCopy = tickets.findById(ticket.getId()).orElseThrow();
        entityManager.detach(staleCopy);

        Ticket currentCopy = tickets.findById(ticket.getId()).orElseThrow();
        currentCopy.assignTo(firstAgent);
        tickets.saveAndFlush(currentCopy);
        staleCopy.assignTo(secondAgent);

        assertThatThrownBy(() -> tickets.saveAndFlush(staleCopy))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private Organization createOrganization(String name) {
        return organizations.saveAndFlush(new Organization(name + " " + UUID.randomUUID(), false, 72, 24, 4));
    }

    private Organization createOrganization(String name, boolean requireApproval) {
        return organizations.saveAndFlush(new Organization(name + " " + UUID.randomUUID(), requireApproval,
                72, 24, 4));
    }

    private Organization createOrganization(String name, int lowHours, int mediumHours, int highHours) {
        return organizations.saveAndFlush(new Organization(name + " " + UUID.randomUUID(), false,
                lowHours, mediumHours, highHours));
    }

    private User createUser(Organization organization, String name, UserRole role) {
        User user = new User(organization, name,
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode(PASSWORD), role);
        return users.saveAndFlush(user);
    }

    private Asset createAsset(Organization organization, String number, String name) {
        return assets.saveAndFlush(new Asset(organization, number, name, "Laptop", null, AssetStatus.ACTIVE));
    }

    private Ticket createTicket(Organization organization, User requester, String title, TicketPriority priority) {
        Instant now = Instant.now();
        int hours = switch (priority) {
            case LOW -> organization.getSlaLowHours();
            case MEDIUM -> organization.getSlaMediumHours();
            case HIGH -> organization.getSlaHighHours();
        };
        return tickets.saveAndFlush(new Ticket(organization, title, "Request description", requester, null,
                priority, now, now.plusSeconds((long) hours * 3600)));
    }

    private Ticket createDashboardTicket(Organization organization, User requester, String title,
            TicketPriority priority, TicketStatus status, Instant dueAt) {
        Instant now = Instant.now();
        Ticket ticket = new Ticket(organization, title, "Dashboard metric fixture", requester, null,
                priority, now, dueAt);
        if (status != TicketStatus.OPEN) {
            ticket.transition(TicketCommand.START, now, false);
        }
        if (status == TicketStatus.PENDING_APPROVAL) {
            ticket.transition(TicketCommand.RESOLVE, now, true);
        } else if (status == TicketStatus.RESOLVED || status == TicketStatus.CLOSED) {
            ticket.transition(TicketCommand.RESOLVE, now, false);
            if (status == TicketStatus.CLOSED) {
                ticket.transition(TicketCommand.CLOSE, now, false);
            }
        }
        return tickets.saveAndFlush(ticket);
    }

    private void assertOrganizationDashboard(MockHttpSession session, int total, int open, int inProgress,
            int pendingApproval, int resolved, int closed, int overdue, int low, int medium, int high,
            int activeAssets) throws Exception {
        mockMvc.perform(get("/api/dashboard").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(total))
                .andExpect(jsonPath("$.openTickets").value(open))
                .andExpect(jsonPath("$.inProgressTickets").value(inProgress))
                .andExpect(jsonPath("$.pendingApprovalTickets").value(pendingApproval))
                .andExpect(jsonPath("$.resolvedTickets").value(resolved))
                .andExpect(jsonPath("$.closedTickets").value(closed))
                .andExpect(jsonPath("$.overdueTickets").value(overdue))
                .andExpect(jsonPath("$.priorityDistribution.low").value(low))
                .andExpect(jsonPath("$.priorityDistribution.medium").value(medium))
                .andExpect(jsonPath("$.priorityDistribution.high").value(high))
                .andExpect(jsonPath("$.activeAssets").value(activeAssets));
    }

    private org.springframework.test.web.servlet.ResultActions performTransition(long ticketId,
            MockHttpSession session, CsrfFixture csrf, String command) throws Exception {
        return mockMvc.perform(post("/api/tickets/{id}/transition", ticketId).session(session)
                .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                .contentType("application/json").content("{\"command\":\"" + command + "\"}"));
    }

    private CsrfFixture fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new CsrfFixture(body.get("headerName").asText(), body.get("token").asText(), cookie);
    }

    private MockHttpSession login(User user, CsrfFixture csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"organizationId":%d,"email":"%s","password":"%s"}
                                """.formatted(user.getOrganization().getId(), user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private record CsrfFixture(String headerName, String token, Cookie cookie) {
    }
}
