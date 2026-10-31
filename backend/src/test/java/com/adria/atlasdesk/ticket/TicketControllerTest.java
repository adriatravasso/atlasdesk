package com.adria.atlasdesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

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

    private Organization createOrganization(String name) {
        return organizations.saveAndFlush(new Organization(name + " " + UUID.randomUUID(), false, 72, 24, 4));
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
