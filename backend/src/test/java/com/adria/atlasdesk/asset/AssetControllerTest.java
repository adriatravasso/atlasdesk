package com.adria.atlasdesk.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssetControllerTest {

    private static final String PASSWORD = "Asset-Test-Password-123";

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
    private PasswordEncoder passwordEncoder;

    @Test
    void adminCanCreateListFilterAndUpdateAssets() throws Exception {
        Organization organization = createOrganization("Asset CRUD");
        User admin = createUser(organization, "Admin User", UserRole.ADMIN);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        MvcResult created = mockMvc.perform(post("/api/assets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":" LT-100 ","name":"Executive Laptop","type":"Laptop","description":"Assigned to support"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetNumber").value("LT-100"))
                .andExpect(jsonPath("$.organizationId").value(organization.getId()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.organization").doesNotExist())
                .andReturn();
        long assetId = objectMapper.readTree(created.getResponse().getContentAsByteArray())
                .get("id").asLong();

        mockMvc.perform(post("/api/assets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":"lt-100","name":"Duplicate Laptop","type":"Laptop"}
                                """))
                .andExpect(status().isConflict());

        MvcResult secondCreated = mockMvc.perform(post("/api/assets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":"PC-200","name":"Design Workstation","type":"Desktop"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long secondAssetId = objectMapper.readTree(secondCreated.getResponse().getContentAsByteArray())
                .get("id").asLong();

        mockMvc.perform(patch("/api/assets/{id}", secondAssetId)
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":"lt-100"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/assets?page=0&size=1").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/assets?status=ACTIVE&type=lApToP&search=lt-10&page=0&size=10")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].assetNumber").value("LT-100"));

        mockMvc.perform(patch("/api/assets/{id}", assetId)
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"name":"Updated Laptop","description":"","status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Laptop"))
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        mockMvc.perform(get("/api/assets?status=INACTIVE").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(assetId));
    }

    @Test
    void agentsCanWriteAndRequestersCanReadButCannotWrite() throws Exception {
        Organization organization = createOrganization("Asset roles");
        User agent = createUser(organization, "Agent User", UserRole.AGENT);
        User requester = createUser(organization, "Requester User", UserRole.REQUESTER);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession agentSession = login(agent, csrf);

        MvcResult created = mockMvc.perform(post("/api/assets")
                        .session(agentSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":"AG-101","name":"Agent Laptop","type":"Laptop"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long assetId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();
        mockMvc.perform(patch("/api/assets/{id}", assetId)
                        .session(agentSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"status":"INACTIVE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));

        MockHttpSession requesterSession = login(requester, csrf);
        mockMvc.perform(get("/api/assets").session(requesterSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("INACTIVE"));
        mockMvc.perform(post("/api/assets")
                        .session(requesterSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":"RQ-202","name":"Requester Device","type":"Tablet"}
                                """))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/assets/{id}", assetId)
                        .session(requesterSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"status":"INACTIVE"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void assetNumbersAreUniqueWithinAnOrganizationAndForeignIdsStayHidden() throws Exception {
        Organization ownOrganization = createOrganization("Own assets");
        Organization foreignOrganization = createOrganization("Foreign assets");
        User ownAdmin = createUser(ownOrganization, "Own Admin", UserRole.ADMIN);
        User foreignAdmin = createUser(foreignOrganization, "Foreign Admin", UserRole.ADMIN);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession ownSession = login(ownAdmin, csrf);
        MockHttpSession foreignSession = login(foreignAdmin, csrf);

        mockMvc.perform(post("/api/assets")
                        .session(ownSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"organizationId":%d,"assetNumber":"SHARED-7","name":"Own Asset","type":"Monitor"}
                                """.formatted(foreignOrganization.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationId").value(ownOrganization.getId()));
        MvcResult foreignCreated = mockMvc.perform(post("/api/assets")
                        .session(foreignSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":"shared-7","name":"Foreign Asset","type":"Monitor"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long foreignAssetId = objectMapper.readTree(foreignCreated.getResponse().getContentAsByteArray())
                .get("id").asLong();

        mockMvc.perform(get("/api/assets").session(ownSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Own Asset"));
        mockMvc.perform(patch("/api/assets/{id}", foreignAssetId)
                        .session(ownSession)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"name":"Cross-tenant update"}
                                """))
                .andExpect(status().isNotFound());

        Asset unchanged = assets.findByIdAndOrganizationId(foreignAssetId, foreignOrganization.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Foreign Asset");
        assertThat(assets.existsByOrganizationIdAndAssetNumberIgnoreCase(
                ownOrganization.getId(), "shared-7")).isTrue();
        assertThat(assets.existsByOrganizationIdAndAssetNumberIgnoreCase(
                foreignOrganization.getId(), "SHARED-7")).isTrue();
    }

    @Test
    void validatesRequiredFieldsPatchAndPagination() throws Exception {
        Organization organization = createOrganization("Asset validation");
        User admin = createUser(organization, "Admin User", UserRole.ADMIN);
        CsrfFixture csrf = fetchCsrf();
        MockHttpSession session = login(admin, csrf);

        mockMvc.perform(post("/api/assets")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("""
                                {"assetNumber":" ","name":"","type":"Laptop"}
                                """))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/assets/1")
                        .session(session)
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/assets?page=-1&size=101").session(session))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/assets?status=UNKNOWN").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void databaseEnforcesCaseInsensitiveAssetNumberUniqueness() {
        Organization organization = createOrganization("Database uniqueness");
        assets.saveAndFlush(new Asset(organization, "DB-asset-9", "First Asset", "Monitor",
                null, AssetStatus.ACTIVE));

        assertThatThrownBy(() -> assets.saveAndFlush(new Asset(organization, "db-ASSET-9", "Duplicate Asset",
                "Monitor", null, AssetStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Organization createOrganization(String name) {
        return organizations.saveAndFlush(new Organization(name + " " + UUID.randomUUID(), false, 72, 24, 4));
    }

    private User createUser(Organization organization, String name, UserRole role) {
        User user = new User(organization, name,
                role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode(PASSWORD), role);
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
