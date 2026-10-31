package com.adria.atlasdesk.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationControllerTest {

    private static final String TEST_PASSWORD = "temporary-test-password";

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void logsInWithValidCredentialsAndReturnsSafeCurrentUser() throws Exception {
        User user = createUser(true);
        CsrfFixture csrf = fetchCsrf();

        MvcResult login = postLogin(user, csrf, TEST_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(user.getId()))
                .andExpect(jsonPath("$.user.organizationId").value(user.getOrganization().getId()))
                .andExpect(jsonPath("$.user.email").value(user.getEmail()))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsInvalidPasswordWithJson401() throws Exception {
        User user = createUser(true);
        CsrfFixture csrf = fetchCsrf();

        postLogin(user, csrf, "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void rejectsInactiveUsers() throws Exception {
        User user = createUser(false);
        CsrfFixture csrf = fetchCsrf();

        postLogin(user, csrf, TEST_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void logoutInvalidatesSessionAndRequiresCsrf() throws Exception {
        User user = createUser(true);
        CsrfFixture csrf = fetchCsrf();
        MvcResult login = postLogin(user, csrf, TEST_PASSWORD).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsLoginWhenCsrfTokenIsMissing() throws Exception {
        User user = createUser(true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(loginBody(user, TEST_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void deniesAnonymousMeWithJson401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void keepsHealthEndpointPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void currentUserReadIgnoresClientSuppliedOrganizationId() throws Exception {
        User user = createUser(true);
        Organization otherOrganization = organizations.saveAndFlush(
                new Organization("Other organization " + UUID.randomUUID(), false, 72, 24, 4));
        CsrfFixture csrf = fetchCsrf();
        MvcResult login = postLogin(user, csrf, TEST_PASSWORD).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me")
                        .session(session)
                        .param("organizationId", otherOrganization.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(user.getOrganization().getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()));
    }

    private User createUser(boolean active) {
        Organization organization = organizations.saveAndFlush(
                new Organization("Auth test " + UUID.randomUUID(), false, 72, 24, 4));
        User user = users.saveAndFlush(new User(organization, "Auth Test User",
                "auth-" + UUID.randomUUID() + "@example.test", passwordEncoder.encode(TEST_PASSWORD), UserRole.AGENT));
        if (!active) {
            jdbcTemplate.update("UPDATE users SET active = FALSE WHERE id = ?", user.getId());
            entityManager.clear();
        }
        return user;
    }

    private CsrfFixture fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        return new CsrfFixture(body.get("headerName").asText(), body.get("token").asText(), cookie);
    }

    private org.springframework.test.web.servlet.ResultActions postLogin(User user, CsrfFixture csrf, String password)
            throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType("application/json")
                .content(objectMapper.writeValueAsBytes(loginBody(user, password))));
    }

    private LoginBody loginBody(User user, String password) {
        return new LoginBody(user.getOrganization().getId(), user.getEmail(), password);
    }

    private record CsrfFixture(String headerName, String token, Cookie cookie) {
    }

    private record LoginBody(Long organizationId, String email, String password) {
    }
}
