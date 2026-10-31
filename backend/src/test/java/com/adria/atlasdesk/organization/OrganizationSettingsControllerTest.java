package com.adria.atlasdesk.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import com.adria.atlasdesk.ticket.Ticket;
import com.adria.atlasdesk.ticket.TicketPriority;
import com.adria.atlasdesk.ticket.TicketRepository;
import com.adria.atlasdesk.ticket.TicketStatus;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(OrganizationSettingsControllerTest.FixedClockConfiguration.class)
class OrganizationSettingsControllerTest {

    private static final String PASSWORD = "Settings-Test-Password-123";
    private static final Instant NOW = Instant.parse("2026-10-31T18:00:00Z");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizations;
    @Autowired private UserRepository users;
    @Autowired private TicketRepository tickets;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void settingsAreTenantScopedAdminWritableAndExistingTicketDeadlinesStayFrozen() throws Exception {
        Organization organization = createOrganization("Settings primary", 72, 24, 8);
        Organization otherOrganization = createOrganization("Settings other", 60, 20, 4);
        User admin = createUser(organization, UserRole.ADMIN);
        User requester = createUser(organization, UserRole.REQUESTER);
        User otherAdmin = createUser(otherOrganization, UserRole.ADMIN);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession adminSession = login(admin, csrf);
        MockHttpSession requesterSession = login(requester, csrf);

        mockMvc.perform(get("/api/settings").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaLowHours").value(72))
                .andExpect(jsonPath("$.slaMediumHours").value(24))
                .andExpect(jsonPath("$.slaHighHours").value(8));

        long originalTicketId = createHighTicket(requesterSession, csrf);
        Instant originalDueAt = tickets.findById(originalTicketId).orElseThrow().getDueAt();
        assertThat(tickets.findById(originalTicketId).orElseThrow().getCreatedAt()).isEqualTo(NOW);
        assertThat(originalDueAt).isEqualTo(NOW.plusSeconds(8 * 3600L));

        mockMvc.perform(patch("/api/settings").session(requesterSession)
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"slaLowHours\":72,\"slaMediumHours\":24,\"slaHighHours\":16}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/settings").session(adminSession)
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"slaLowHours\":72,\"slaMediumHours\":24,\"slaHighHours\":16}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaHighHours").value(16));

        mockMvc.perform(get("/api/settings").session(requesterSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slaHighHours").value(16));
        MockHttpSession otherAdminSession = login(otherAdmin, csrf);
        mockMvc.perform(get("/api/settings").session(otherAdminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slaLowHours").value(60))
                .andExpect(jsonPath("$.slaMediumHours").value(20))
                .andExpect(jsonPath("$.slaHighHours").value(4));

        long newTicketId = createHighTicket(requesterSession, csrf);
        assertThat(tickets.findById(originalTicketId).orElseThrow().getDueAt()).isEqualTo(originalDueAt);
        assertThat(tickets.findById(newTicketId).orElseThrow().getDueAt()).isEqualTo(NOW.plusSeconds(16 * 3600L));
    }

    @Test
    void rejectsSlaValuesOutsideOneToSevenHundredTwentyHours() throws Exception {
        Organization organization = createOrganization("Settings validation", 72, 24, 8);
        User admin = createUser(organization, UserRole.ADMIN);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        for (String body : new String[] {
                "{\"slaLowHours\":0,\"slaMediumHours\":24,\"slaHighHours\":8}",
                "{\"slaLowHours\":72,\"slaMediumHours\":721,\"slaHighHours\":8}"}) {
            mockMvc.perform(patch("/api/settings").session(session)
                            .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                            .contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void ticketReadMarksExpiredOpenTicketOverdueUsingTheControlledUtcClock() throws Exception {
        Organization organization = createOrganization("Overdue ticket", 72, 24, 8);
        User requester = createUser(organization, UserRole.REQUESTER);
        Ticket expiredTicket = new Ticket(organization, "Expired", "Expired deadline", requester, null,
                TicketPriority.HIGH, NOW.minusSeconds(2 * 3600L), NOW.minusSeconds(1));
        tickets.saveAndFlush(expiredTicket);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession requesterSession = login(requester, csrf);

        mockMvc.perform(get("/api/tickets/{id}", expiredTicket.getId()).session(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").value(NOW.minusSeconds(1).toString()))
                .andExpect(jsonPath("$.overdue").value(true));
    }

    @Test
    void overdueIsComputedFromUtcClockAndResolvedOrClosedTicketsAreNotOverdue() {
        assertThat(summary(TicketStatus.OPEN, NOW.minusSeconds(1)).withOverdue(NOW).overdue()).isTrue();
        assertThat(summary(TicketStatus.IN_PROGRESS, NOW).withOverdue(NOW).overdue()).isFalse();
        assertThat(summary(TicketStatus.PENDING_APPROVAL, NOW.minusSeconds(1)).withOverdue(NOW).overdue()).isTrue();
        assertThat(summary(TicketStatus.RESOLVED, NOW.minusSeconds(1)).withOverdue(NOW).overdue()).isFalse();
        assertThat(summary(TicketStatus.CLOSED, NOW.minusSeconds(1)).withOverdue(NOW).overdue()).isFalse();
        assertThat(summary(TicketStatus.OPEN, null).withOverdue(NOW).overdue()).isFalse();
    }

    private static com.adria.atlasdesk.ticket.TicketSummary summary(TicketStatus status, Instant dueAt) {
        return new com.adria.atlasdesk.ticket.TicketSummary(1L, 1L, "Title", "Description", 1L, "Requester",
                null, null, null, null, TicketPriority.HIGH, status, NOW, NOW, dueAt, null, null);
    }

    private long createHighTicket(MockHttpSession session, CsrfFixture csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tickets").session(session)
                        .cookie(csrf.cookie()).header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{\"title\":\"High priority\",\"description\":\"SLA snapshot\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()))
                .andExpect(jsonPath("$.overdue").value(false))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return body.get("id").asLong();
    }

    private Organization createOrganization(String name, int low, int medium, int high) {
        return organizations.saveAndFlush(new Organization(name + " " + UUID.randomUUID(), false, low, medium, high));
    }

    private User createUser(Organization organization, UserRole role) {
        return users.saveAndFlush(new User(organization, role.name() + " User",
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode(PASSWORD), role));
    }

    private CsrfFixture fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        return new CsrfFixture(body.get("headerName").asText(), body.get("token").asText(), cookie);
    }

    private MockHttpSession login(User user, CsrfFixture csrf) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()).contentType("application/json")
                        .content("{\"organizationId\":%d,\"email\":\"%s\",\"password\":\"%s\"}"
                                .formatted(user.getOrganization().getId(), user.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private record CsrfFixture(String headerName, String token, Cookie cookie) { }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
