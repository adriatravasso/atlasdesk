package com.adria.atlasdesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAdminControllerTest {

    private static final String PASSWORD = "Initial-Mvp-Password-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminCanCreateListUpdateAndDeactivateUsers() throws Exception {
        Organization organization = createOrganization("CRUD");
        User admin = createUser(organization, "Admin User", UserRole.ADMIN, true);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        MvcResult created = mockMvc.perform(post("/api/users")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("""
                                {"name":"New Agent","email":"new-agent@example.test","role":"AGENT","password":"Initial-Mvp-Password-123"}
                                """)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Agent"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andReturn();
        Long createdId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(get("/api/users?page=0&size=1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(patch("/api/users/{id}", createdId)
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("""
                                {"name":"Updated Agent","email":"updated-agent@example.test","role":"REQUESTER","active":false,"password":"Updated-Mvp-Password-456"}
                                """)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Agent"))
                .andExpect(jsonPath("$.role").value("REQUESTER"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User updated = users.findByIdAndOrganizationId(createdId, organization.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("updated-agent@example.test");
        assertThat(passwordEncoder.matches("Updated-Mvp-Password-456", updated.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsDuplicateEmailWithinOrganization() throws Exception {
        Organization organization = createOrganization("Duplicate");
        User admin = createUser(organization, "Admin User", UserRole.ADMIN, true);
        createUser(organization, "Existing User", UserRole.AGENT, true, "duplicate@example.test");
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("""
                                {"name":"Duplicate","email":"DUPLICATE@example.test","role":"REQUESTER","password":"Initial-Mvp-Password-123"}
                                """)))
                .andExpect(status().isConflict());
    }

    @Test
    void agentsAndRequestersCannotAdministerUsers() throws Exception {
        Organization organization = createOrganization("Role access");
        User target = createUser(organization, "Target User", UserRole.AGENT, true);
        CsrfFixture csrf = fetchCsrf();

        for (UserRole role : new UserRole[] {UserRole.AGENT, UserRole.REQUESTER}) {
            User user = createUser(organization, "Test " + role, role, true);
            MockHttpSession session = login(user, csrf);

            mockMvc.perform(get("/api/users").session(session))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("forbidden"));
            mockMvc.perform(post("/api/users")
                            .session(session)
                            .cookie(csrf.cookie())
                            .header(csrf.headerName(), csrf.token())
                            .contentType("application/json")
                            .content(json("""
                                    {"name":"Denied","email":"denied-%s@example.test","role":"REQUESTER","password":"Initial-Mvp-Password-123"}
                                    """.formatted(role.name().toLowerCase()))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/users/{id}", target.getId())
                            .session(session)
                            .cookie(csrf.cookie())
                            .header(csrf.headerName(), csrf.token())
                            .contentType("application/json")
                            .content(json("{\"active\":false}")))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void crossTenantListAndUpdateCannotReadOrChangeForeignUsers() throws Exception {
        Organization ownOrganization = createOrganization("Own");
        User admin = createUser(ownOrganization, "Admin User", UserRole.ADMIN, true);
        Organization foreignOrganization = createOrganization("Foreign");
        User foreign = createUser(foreignOrganization, "Foreign User", UserRole.AGENT, true);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        mockMvc.perform(get("/api/users?page=0&size=100").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(admin.getId()));
        mockMvc.perform(patch("/api/users/{id}", foreign.getId())
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("{\"active\":false}")))
                .andExpect(status().isNotFound());

        assertThat(users.findById(foreign.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void cannotDeactivateOrDemoteTheLastActiveAdmin() throws Exception {
        Organization organization = createOrganization("Last admin");
        User admin = createUser(organization, "Only Admin", UserRole.ADMIN, true);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        mockMvc.perform(patch("/api/users/{id}", admin.getId())
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("{\"active\":false}")))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/users/{id}", admin.getId())
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("{\"role\":\"AGENT\"}")))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("""
                                {"name":"Second Admin","email":"second-admin@example.test","role":"ADMIN","password":"Initial-Mvp-Password-123"}
                                """)))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/users/{id}", admin.getId())
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("{\"active\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(users.countByOrganizationIdAndRoleAndActiveTrue(organization.getId(), UserRole.ADMIN)).isEqualTo(1);
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesInputAndPaginationBounds() throws Exception {
        Organization organization = createOrganization("Validation");
        User admin = createUser(organization, "Admin User", UserRole.ADMIN, true);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        mockMvc.perform(post("/api/users")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content(json("""
                                {"name":" ","email":"not-an-email","role":"INVALID","password":"short"}
                                """)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/users?page=-1&size=101").session(session))
                .andExpect(status().isBadRequest());
    }

    private Organization createOrganization(String name) {
        return organizations.saveAndFlush(new Organization(name + " " + UUID.randomUUID(), false, 72, 24, 4));
    }

    private User createUser(Organization organization, String name, UserRole role, boolean active) {
        return createUser(organization, name, role, active,
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test");
    }

    private User createUser(Organization organization, String name, UserRole role, boolean active, String email) {
        User user = new User(organization, name, email, passwordEncoder.encode(PASSWORD), role);
        if (!active) {
            user.deactivate();
        }
        return users.saveAndFlush(user);
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
                        .content(json("""
                                {"organizationId":%d,"email":"%s","password":"%s"}
                                """.formatted(user.getOrganization().getId(), user.getEmail(), PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String json(String input) {
        return input;
    }

    private record CsrfFixture(String headerName, String token, Cookie cookie) {
    }
}
